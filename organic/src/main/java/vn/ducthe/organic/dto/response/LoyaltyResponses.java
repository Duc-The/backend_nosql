package vn.ducthe.organic.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import vn.ducthe.organic.entity.CustomerPointTransactionEntity.Type;
import vn.ducthe.organic.entity.PromotionEntity;

public final class LoyaltyResponses {
    private LoyaltyResponses() {}

    public record Balance(long points, BigDecimal spendPerPoint, Offer offer) {}

    public record Offer(long pointsRequired, BigDecimal voucherValue, BigDecimal minimumOrderValue, long expiresIn) {}

    public record Voucher(String id, String code, String status, Integer usageLimit, long usageCount,
                          Instant issuedAt, Instant expiresAt, String name, PromotionEntity.DiscountType discountType,
                          BigDecimal discountValue, BigDecimal minimumOrderValue) {}

    public record PointEntry(String id, String orderId, String voucherId, Type type, long pointsDelta,
                             long balanceAfter, String reason, Instant occurredAt) {}
}
