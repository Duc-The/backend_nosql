package vn.ducthe.organic.service.impl;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.dto.response.JWTResponses.Tokens;
import vn.ducthe.organic.entity.AccountEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.RedisAuthTokens;
import vn.ducthe.organic.service.JWTService;
import vn.ducthe.organic.service.impl.RoleAccess;

@Service
public class JWTServiceImpl implements JWTService {
    private final JwtEncoder encoder;
    private final RedisAuthTokens tokens;
    private final RoleAccess roles;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JWTServiceImpl(JwtEncoder encoder, RedisAuthTokens tokens, RoleAccess roles,
                          @Value("${app.jwt.issuer}") String issuer,
                          @Value("${app.jwt.access-token-ttl}") Duration accessTtl,
                          @Value("${app.jwt.refresh-token-ttl}") Duration refreshTtl) {
        this.encoder = encoder;
        this.tokens = tokens;
        this.roles = roles;
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    @Override
    public Tokens issue(AccountEntity account) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(account.getId())
                .issuedAt(now).expiresAt(now.plus(accessTtl))
                .claim("ver", AuthStore.credentialVersion(account))
                .claim("username", account.getUsername()).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new Tokens(accessToken, tokens.issue("refresh", account, refreshTtl), "Bearer", accessTtl.toSeconds(),
                roles.roles(account.getId()));
    }

    @Override
    public Tokens refresh(String refreshToken) {
        return issue(tokens.consume("refresh", refreshToken, ApiError.INVALID_TOKEN));
    }
}
