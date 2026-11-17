package vn.ducthe.organic.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.ducthe.organic.entity.CustomerVoucherEntity;
import vn.ducthe.organic.entity.OrderDetailEntity;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.PromotionEntity;
import vn.ducthe.organic.service.impl.OrderInventory;

public interface PromotionService {

    Quote preview(String customerId, String rawCode, BigDecimal subtotal, List<PriceLine> lines, Instant now);

    List<OrderInventory.PlannedBatch> claim(OrderEntity order, List<OrderDetailEntity> lines,
                                                  Quote quote, Instant now);

    List<OrderInventory.PlannedBatch> requiredBatches(String orderId);

    void release(String orderId, Instant now);

    String appliedCode(String orderId);

    record PriceLine(String productId, String variantId, BigDecimal quantity, BigDecimal unitPrice) {}

    record Part(String variantId, String batchId, BigDecimal quantity, BigDecimal amount) {}

    record Quote(String code, PromotionEntity promotion, CustomerVoucherEntity voucher,
                        BigDecimal eligibleAmount, BigDecimal discount, List<Part> parts) {}
}
