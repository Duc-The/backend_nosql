package vn.ducthe.organic.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.PaymentEntity;
import vn.ducthe.organic.service.impl.OrderInventory;

public final class OrderResponses {
    private OrderResponses() {}

    public record Summary(String orderCode, String status, String paymentStatus, PaymentEntity.Method paymentMethod,
                          String currency, BigDecimal totalAmount, Instant orderedAt, Instant paymentDueAt,
                          Instant stockReservedUntil, Instant cancelUntil, boolean canCancel) {}

    public record WarehouseQueueItem(String orderCode, String status, PaymentEntity.Method paymentMethod,
                                     String paymentStatus, Instant orderedAt, Instant stockReservedUntil) {}

    public record WarehouseOrder(WarehouseQueueItem order, Instant stockIssuedAt, String stockIssuedByAccountId,
                                 List<OrderInventory.Allocation> allocations) {}

    public record Detail(Summary order, OrderEntity.AddressSnapshot shippingAddress, BigDecimal subtotalAmount,
                         BigDecimal shippingFee, BigDecimal promotionDiscount, String promotionCode, long pointsEarned,
                         String note, Tracking tracking, Instant completedAt,
                         Instant cancelledAt, String cancellationReason, List<Line> items) {}

    public record Tracking(Instant confirmedAt, Instant packingStartedAt, Instant packedAt, Instant shippedAt,
                           Instant deliveredAt, String carrierName, String trackingCode) {}

    public record Line(String productId, String variantId, String productName, String variantName, String sku,
                       String unitCode, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
