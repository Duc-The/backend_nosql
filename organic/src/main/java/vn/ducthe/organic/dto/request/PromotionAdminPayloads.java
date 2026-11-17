package vn.ducthe.organic.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.ducthe.organic.entity.PromotionEntity;

public final class PromotionAdminPayloads {
    private PromotionAdminPayloads() {}

    public enum Status { ACTIVE, INACTIVE }

    public record Input(@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String promotionCode,
                 @NotBlank @Size(max = 180) String name, @Size(max = 1000) String description,
                 @NotNull PromotionEntity.Audience audience, @NotNull PromotionEntity.Target target,
                 @NotNull PromotionEntity.DiscountType discountType,
                 @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal discountValue,
                 @Positive @Digits(integer = 12, fraction = 0) BigDecimal maxDiscountAmount,
                 @PositiveOrZero @Digits(integer = 12, fraction = 0) BigDecimal minOrderValue,
                 @NotNull Instant startsAt, @NotNull Instant endsAt,
                 @Positive Integer usageLimit, @Positive Integer perCustomerLimit, @NotNull Status status,
                 @Size(max = 200) List<@NotNull @Valid TargetRule> targets) {}

    public record TargetRule(@NotBlank @Size(max = 128) String id,
                      @Positive @Digits(integer = 9, fraction = 3) BigDecimal minimumQuantity) {}

    public record StatusInput(@NotNull Status status) {}

    public record VoucherInput(@NotBlank @Size(max = 128) String customerId, @Positive Integer usageLimit, Instant expiresAt) {}
}
