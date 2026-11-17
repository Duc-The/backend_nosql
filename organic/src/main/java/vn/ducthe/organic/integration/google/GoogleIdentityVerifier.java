package vn.ducthe.organic.integration.google;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;

@Component
public class GoogleIdentityVerifier {
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private final JwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
    private final String clientId;

    public GoogleIdentityVerifier(@Value("${app.google-oauth2.client-id:}") String clientId) {
        this.clientId = clientId;
    }

    public Jwt verify(String idToken) {
        if (clientId.isBlank()) {
            throw new ApiException(ApiError.GOOGLE_UNAVAILABLE);
        }
        try {
            Jwt token = decoder.decode(idToken);
            String issuer = token.getClaimAsString("iss");
            String authorizedParty = token.getClaimAsString("azp");
            if (issuer != null && ISSUERS.contains(issuer)
                    && token.getAudience() != null && token.getAudience().contains(clientId)
                    && (authorizedParty == null ? token.getAudience().size() == 1 : clientId.equals(authorizedParty))
                    && token.getExpiresAt() != null && token.getExpiresAt().isAfter(java.time.Instant.now())
                    && token.getSubject() != null && !token.getSubject().isBlank()
                    && Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified"))
                    && token.getClaimAsString("email") != null && !token.getClaimAsString("email").isBlank()) {
                return token;
            }
        } catch (BadJwtException exception) {
            throw new ApiException(ApiError.INVALID_CREDENTIALS);
        } catch (JwtException exception) {
            throw new ApiException(ApiError.GOOGLE_UNAVAILABLE);
        }
        throw new ApiException(ApiError.INVALID_CREDENTIALS);
    }
}
