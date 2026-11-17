package vn.ducthe.organic.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import vn.ducthe.organic.dto.response.CartResponses;
import vn.ducthe.organic.dto.response.OrderResponses.Tracking;
import vn.ducthe.organic.entity.BaseEntity;
import vn.ducthe.organic.entity.OrderDetailEntity;
import vn.ducthe.organic.entity.OrderEntity;

public final class OrderMapper {
    private OrderMapper() {}

    public static Tracking tracking(OrderEntity order) {
        return new Tracking(order.getConfirmedAt(), order.getPackingStartedAt(), order.getStockIssuedAt(),
                order.getShippedAt(), order.getDeliveredAt(), order.getCarrierName(), order.getTrackingCode());
    }

    public static OrderDetailEntity line(String orderId, CartResponses.Item item, Instant now) {
        OrderDetailEntity line = new OrderDetailEntity();
        initialize(line, now);
        line.setOrderId(orderId);
        line.setProductId(item.productId());
        line.setVariantId(item.variantId());
        line.setProductName(item.productName());
        line.setVariantName(item.variantName());
        line.setSku(item.sku());
        line.setUnitCode(item.unitCode());
        line.setQuantity(BigDecimal.valueOf(item.quantity()));
        line.setUnitPrice(item.unitPrice());
        line.setLineTotal(item.lineTotal());
        line.setDiscountAmount(BigDecimal.ZERO);
        line.setVatRate(BigDecimal.ZERO);
        return line;
    }

    public static void initialize(BaseEntity entity, Instant now) {
        entity.setId(UUID.randomUUID().toString());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
    }
}
