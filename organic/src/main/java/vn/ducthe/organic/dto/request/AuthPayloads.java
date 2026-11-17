package vn.ducthe.organic.dto.request;

import jakarta.validation.constraints.*;

public final class AuthPayloads {
    private AuthPayloads() {}

    public record Register(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,32}") String username,
            @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String fullName) {}

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record OtpRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String otp) {}

    public record Login(@NotBlank @Size(max = 254) String identifier,
            @NotBlank @Size(max = 72) String password) {}

    public record GoogleLogin(@NotBlank @Size(max = 8192) String idToken) {}

    public record Refresh(@NotBlank @Size(max = 128) String refreshToken) {}

    public record Reset(@NotBlank @Size(max = 128) String resetToken,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record ChangePassword(@NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}
}
