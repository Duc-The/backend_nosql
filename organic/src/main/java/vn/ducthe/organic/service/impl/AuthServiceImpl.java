package vn.ducthe.organic.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.dto.request.AuthPayloads;
import vn.ducthe.organic.dto.response.AuthResponses;
import vn.ducthe.organic.dto.response.JWTResponses;
import vn.ducthe.organic.entity.AccountEntity;
import vn.ducthe.organic.entity.AccountIdentityEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.integration.google.GoogleIdentityVerifier;
import vn.ducthe.organic.integration.mail.OtpMailSender;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.RedisAuthTokens;
import vn.ducthe.organic.service.AuthService;
import vn.ducthe.organic.service.JWTService;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String REGISTRATION = "registration";
    private static final String RESET = "reset";
    private static final Duration RESET_TTL = Duration.ofMinutes(10);

    private final AuthStore store;
    private final PasswordEncoder passwords;
    private final RedisAuthTokens redis;
    private final OtpMailSender mail;
    private final JWTService jwt;
    private final GoogleIdentityVerifier google;
    private final String dummyHash;

    public AuthServiceImpl(AuthStore store, PasswordEncoder passwords, RedisAuthTokens redis, OtpMailSender mail,
                       JWTService jwt, GoogleIdentityVerifier google) {
        this.store = store;
        this.passwords = passwords;
        this.redis = redis;
        this.mail = mail;
        this.jwt = jwt;
        this.google = google;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Override
    public void register(AuthPayloads.Register request) {
        String email = normalize(request.email());
        String username = normalize(request.username());
        if (store.byEmail(email) != null || store.byUsername(username) != null) {
            throw new ApiException(ApiError.CONFLICT);
        }
        AccountEntity account = store.create(username, email, request.fullName().strip(),
                encode(request.password()), null);
        sendRegistration(email, account);
    }

    @Override
    public void resend(String address) {
        String email = normalize(address);
        AccountEntity account = store.byEmail(email);
        requireStatus(account, AuthStore.PENDING);
        sendRegistration(email, account);
    }

    @Override
    public void verifyRegistration(AuthPayloads.OtpRequest request) {
        AccountEntity account = store.byEmail(normalize(request.email()));
        requireStatus(account, AuthStore.PENDING);
        redis.verifyOtp(REGISTRATION, account, request.otp());
        store.activate(account);
    }

    @Override
    public JWTResponses.Tokens login(AuthPayloads.Login request) {
        String identifier = normalize(request.identifier());
        AccountEntity account = identifier.contains("@") ? store.byEmail(identifier) : store.byUsername(identifier);
        verifyPassword(account, request.password());
        return login(account);
    }

    @Override
    public JWTResponses.Tokens googleLogin(String idToken) {
        Jwt token = google.verify(idToken);
        String email = normalize(token.getClaimAsString("email"));
        String subject = token.getSubject();
        AccountIdentityEntity identity = store.googleIdentity(subject);
        if (identity != null) {
            return login(store.activeAccount(identity.getAccountId()));
        }
        if (store.byEmail(email) != null) {
            throw new ApiException(ApiError.CONFLICT);
        }
        String name = token.getClaimAsString("name");
        AccountEntity account = store.create("google_" + UUID.randomUUID().toString().replace("-", ""),
                email, name == null || name.isBlank() ? "Khách hàng Google" : name.strip(), null, subject);
        return login(account);
    }

    @Override
    public void forgot(String address) {
        String email = normalize(address);
        if (!redis.allowSend(RESET, email)) {
            return;
        }
        AccountEntity account = store.byEmail(email);
        if (account == null || !AuthStore.ACTIVE.equals(account.getStatus()) || account.getPasswordHash() == null) {
            return;
        }
        try {
            mail.send(email, "Đặt lại mật khẩu Organic", redis.createOtp(RESET, account));
        } catch (ApiException exception) {
            if (exception.error() != ApiError.EMAIL_UNAVAILABLE) {
                throw exception;
            }
        }
    }

    @Override
    public AuthResponses.ResetGrant verifyReset(AuthPayloads.OtpRequest request) {
        AccountEntity account = store.byEmail(normalize(request.email()));
        requireStatus(account, AuthStore.ACTIVE);
        redis.verifyOtp(RESET, account, request.otp());
        return new AuthResponses.ResetGrant(redis.issue(RESET, account, RESET_TTL), RESET_TTL.toSeconds());
    }

    @Override
    public void reset(AuthPayloads.Reset request) {
        String hash = encode(request.newPassword());
        AccountEntity account = redis.consume(RESET, request.resetToken(), ApiError.INVALID_RESET_TOKEN);
        store.updatePassword(account, hash);
    }

    @Override
    public void changePassword(String id, AuthPayloads.ChangePassword request) {
        AccountEntity account = store.activeAccount(id);
        verifyPassword(account, request.currentPassword());
        store.updatePassword(account, encode(request.newPassword()));
    }

    private void sendRegistration(String email, AccountEntity account) {
        if (!redis.allowSend(REGISTRATION, email)) {
            throw new ApiException(ApiError.OTP_RATE_LIMIT);
        }
        mail.send(email, "Xác minh tài khoản Organic", redis.createOtp(REGISTRATION, account));
    }

    private JWTResponses.Tokens login(AccountEntity account) {
        if (account == null) {
            throw new ApiException(ApiError.ACCOUNT_LOCKED);
        }
        if (AuthStore.PENDING.equals(account.getStatus())) {
            throw new ApiException(ApiError.ACCOUNT_UNVERIFIED);
        }
        if (!AuthStore.ACTIVE.equals(account.getStatus())) {
            throw new ApiException(ApiError.ACCOUNT_LOCKED);
        }
        JWTResponses.Tokens tokens = jwt.issue(account);
        store.recordLogin(account.getId());
        return tokens;
    }

    private void verifyPassword(AccountEntity account, String password) {
        String hash = account == null ? null : account.getPasswordHash();
        if (password.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(password, hash == null ? dummyHash : hash) || hash == null) {
            throw new ApiException(ApiError.INVALID_CREDENTIALS);
        }
    }

    private String encode(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(ApiError.VALIDATION_ERROR);
        }
        return passwords.encode(password);
    }

    private static void requireStatus(AccountEntity account, String status) {
        if (account == null || !status.equals(account.getStatus())) {
            throw new ApiException(ApiError.INVALID_OTP);
        }
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
