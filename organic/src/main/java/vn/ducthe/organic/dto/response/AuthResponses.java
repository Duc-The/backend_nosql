package vn.ducthe.organic.dto.response;

import jakarta.validation.constraints.*;

public final class AuthResponses {
    private AuthResponses() {}

    public record ResetGrant(String resetToken, long expiresIn) {}
}
