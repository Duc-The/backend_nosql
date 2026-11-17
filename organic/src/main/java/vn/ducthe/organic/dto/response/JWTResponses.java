package vn.ducthe.organic.dto.response;

import java.util.List;
import vn.ducthe.organic.entity.RoleEntity;

public final class JWTResponses {
    private JWTResponses() {}

    public record Tokens(String accessToken, String refreshToken, String tokenType, long expiresIn, List<RoleEntity.Code> roles) {}
}
