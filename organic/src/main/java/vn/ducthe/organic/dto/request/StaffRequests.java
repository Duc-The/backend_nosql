package vn.ducthe.organic.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.service.StaffService;
import vn.ducthe.organic.service.StaffService.StaffRole;

public final class StaffRequests {
    private StaffRequests() {}

    public record StatusInput(@NotNull StaffService.StaffStatus status) {}

    public record RoleInput(@NotNull StaffService.StaffRole role) {}

    public record PasswordInput(@NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record Create(@NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,32}") String username,
                         @NotBlank @Size(min = 8, max = 72) String password,
                         @NotNull StaffRole role, @NotNull @Valid Profile profile) {}

    public record Profile(@NotBlank @Size(max = 100) String fullName,
                          @Email @Size(max = 254) String email,
                          @Pattern(regexp = "\\+?[0-9]{9,15}") String phoneNumber,
                          @Size(max = 100) String jobTitle, @PastOrPresent LocalDate hiredOn) {}
}
