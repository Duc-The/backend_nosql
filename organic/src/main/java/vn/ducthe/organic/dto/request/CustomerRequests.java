package vn.ducthe.organic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class CustomerRequests {
    private CustomerRequests() {}

    public record ProfileInput(@NotBlank @Size(max = 100) String fullName,
            @Pattern(regexp = "\\+?[0-9]{9,15}") String phoneNumber, @PastOrPresent LocalDate dateOfBirth) {}

    public record AddressInput(@NotBlank @Size(max = 50) String label,
            @NotBlank @Size(max = 100) String receiverName,
            @NotBlank @Pattern(regexp = "\\+?[0-9]{9,15}") String receiverPhone,
            @NotBlank @Size(max = 100) String province, @Size(max = 100) String district,
            @NotBlank @Size(max = 100) String ward, @NotBlank @Size(max = 250) String streetAddress) {}
}
