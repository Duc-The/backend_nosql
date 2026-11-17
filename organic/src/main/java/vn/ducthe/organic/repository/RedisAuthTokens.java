package vn.ducthe.organic.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import vn.ducthe.organic.entity.AccountEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;

@Component
public class RedisAuthTokens {
    private static final DefaultRedisScript<Long> VERIFY = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return 0 end
            local code, tries = string.match(value, '^(%x+):(%d+)$')
            if code == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 1
            end
            if tonumber(tries) <= 1 then redis.call('DEL', KEYS[1])
            else redis.call('SET', KEYS[1], code .. ':' .. (tonumber(tries) - 1), 'KEEPTTL') end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AuthStore store;
    private final SecureRandom random = new SecureRandom();

    public RedisAuthTokens(StringRedisTemplate redis, AuthStore store) {
        this.redis = redis;
        this.store = store;
    }

    public boolean allowSend(String purpose, String email) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                "organic:auth:cooldown:" + purpose + ":" + hash(email), "1", Duration.ofSeconds(60)));
    }

    public String createOtp(String purpose, AccountEntity account) {
        String key = otpKey(purpose, account);
        String otp = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(key, hash(key + ":" + otp) + ":5", Duration.ofMinutes(5));
        return otp;
    }

    public void verifyOtp(String purpose, AccountEntity account, String otp) {
        String key = otpKey(purpose, account);
        if (!Long.valueOf(1).equals(redis.execute(VERIFY, List.of(key), hash(key + ":" + otp)))) {
            throw new ApiException(ApiError.INVALID_OTP);
        }
    }

    public String issue(String purpose, AccountEntity account, Duration ttl) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        redis.opsForValue().set(tokenKey(purpose, token),
                account.getId() + ":" + AuthStore.credentialVersion(account), ttl);
        return token;
    }

    public AccountEntity consume(String purpose, String token, ApiError error) {
        String value = redis.opsForValue().getAndDelete(tokenKey(purpose, token));
        if (value == null) {
            throw new ApiException(error);
        }
        String[] parts = value.split(":", 2);
        AccountEntity account = store.activeAccount(parts[0]);
        if (account == null || !AuthStore.credentialVersion(account).equals(parts[1])) {
            throw new ApiException(error);
        }
        return account;
    }

    private static String otpKey(String purpose, AccountEntity account) {
        return "organic:auth:otp:" + purpose + ":" + account.getId() + ":" + AuthStore.credentialVersion(account);
    }

    private static String tokenKey(String purpose, String token) {
        return "organic:auth:token:" + purpose + ":" + hash(token);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
