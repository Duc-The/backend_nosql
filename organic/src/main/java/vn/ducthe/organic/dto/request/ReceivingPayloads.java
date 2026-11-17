package vn.ducthe.organic.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReceivingPayloads {
    private ReceivingPayloads() {}

    public enum SupplierStatus { ACTIVE, INACTIVE }
    public enum ReceiptStatus { DRAFT, CONFIRMED, CANCELLED }

    public record SupplierInput(@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String supplierCode,
                         @NotBlank @Size(max = 200) String name, @Size(max = 32) String taxCode,
                         @Size(max = 100) String contactPerson,
                         @Pattern(regexp = "\\+?[0-9]{9,15}") String phoneNumber,
                         @Email @Size(max = 254) String email, @Size(max = 250) String address,
                         @Size(max = 500) String note) {}

    public record SupplierStatusInput(@NotNull SupplierStatus status) {}

    public record ReceiptInput(@NotBlank @Size(max = 128) String supplierId, @Size(max = 500) String note,
                        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid LineInput> items) {}

    public record LineInput(@NotBlank @Size(max = 128) String variantId,
                     @NotNull @Positive @Digits(integer = 9, fraction = 0) BigDecimal quantityReceived,
                     @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 0) BigDecimal quantityRejected,
                     @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 0) BigDecimal unitCost,
                     @Size(max = 500) String discrepancyReason, @Size(max = 80) String lotCode,
                     @PastOrPresent LocalDate manufactureDate, LocalDate expiryDate,
                     @Size(max = 500) String originDescription) {}

    public record ConfirmationInput(@Size(max = 500) String note) {}
    public record CancellationInput(@NotBlank @Size(max = 500) String reason) {}
}
