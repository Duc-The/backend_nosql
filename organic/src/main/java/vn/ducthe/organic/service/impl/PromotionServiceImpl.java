package vn.ducthe.organic.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.entity.CustomerVoucherEntity;
import vn.ducthe.organic.entity.OrderDetailEntity;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.OrderPromotionEntity;
import vn.ducthe.organic.entity.PromotionBatchEntity;
import vn.ducthe.organic.entity.PromotionEntity;
import vn.ducthe.organic.entity.PromotionProductEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.service.PromotionService;
import vn.ducthe.organic.service.impl.OrderInventory;

@Service
public class PromotionServiceImpl implements PromotionService {
    private final MongoDocumentRepository mongo;
    private final OrderInventory inventory;

    public PromotionServiceImpl(MongoDocumentRepository mongo, OrderInventory inventory) {
        this.mongo = mongo;
        this.inventory = inventory;
    }

    @Override
    public Quote preview(String customerId, String rawCode, BigDecimal subtotal, List<PriceLine> lines, Instant now) {
        String code = normalize(rawCode);
        CustomerVoucherEntity voucher = mongo.findOne(Query.query(Criteria.where("voucherCode").is(code)
                .and("customerId").is(customerId)), CustomerVoucherEntity.class);
        PromotionEntity promotion = voucher == null
                ? mongo.findOne(by("promotionCode", code), PromotionEntity.class)
                : mongo.findById(voucher.getPromotionId(), PromotionEntity.class);
        if (promotion == null || promotion.getTarget() == null
                || !activeAt(promotion, now) || exhausted(promotion.getUsageLimit(), promotion.getUsageCount())
                || promotion.getAudience() != (voucher == null ? PromotionEntity.Audience.ALL_CUSTOMERS
                        : PromotionEntity.Audience.ASSIGNED_CUSTOMERS)
                || voucher != null && !usable(voucher, now)) {
            throw new ApiException(ApiError.PROMOTION_UNAVAILABLE);
        }

        Integer limit = promotion.getPerCustomerLimit();
        if (limit != null && mongo.count(Query.query(Criteria.where("customerId").is(customerId)
                .and("promotionId").is(promotion.getId()).and("releasedAt").is(null)),
                OrderPromotionEntity.class) >= limit) {
            throw new ApiException(ApiError.PROMOTION_UNAVAILABLE);
        }

        if (subtotal.signum() <= 0 || promotion.getMinOrderValue() != null
                && subtotal.compareTo(promotion.getMinOrderValue()) < 0) {
            throw new ApiException(ApiError.PROMOTION_MINIMUM_NOT_MET);
        }
        BigDecimal value = promotion.getDiscountValue();
        if (value == null || value.signum() <= 0 || promotion.getDiscountType() == null) {
            throw new ApiException(ApiError.PROMOTION_UNAVAILABLE);
        }
        List<Part> eligible = switch (promotion.getTarget()) {
            case ORDER -> lines.stream().map(line -> new Part(line.variantId(), null, line.quantity(),
                    line.unitPrice().multiply(line.quantity()))).toList();
            case PRODUCT -> eligibleProducts(promotion.getId(), lines);
            case BATCH -> eligibleBatches(promotion.getId(), lines, now);
        };
        BigDecimal eligibleAmount = eligible.stream().map(Part::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (eligibleAmount.signum() <= 0) {
            throw new ApiException(ApiError.PROMOTION_SCOPE_NOT_MET);
        }
        BigDecimal discount = promotion.getDiscountType() == PromotionEntity.DiscountType.PERCENTAGE
                ? eligibleAmount.multiply(value).movePointLeft(2) : value;
        if (promotion.getMaxDiscountAmount() != null) {
            discount = discount.min(promotion.getMaxDiscountAmount());
        }
        discount = discount.min(eligibleAmount).setScale(0, RoundingMode.DOWN);
        if (discount.signum() <= 0) {
            throw new ApiException(ApiError.PROMOTION_MINIMUM_NOT_MET);
        }
        return new Quote(code, promotion, voucher, eligibleAmount, discount, eligible);
    }

    private List<Part> eligibleProducts(String promotionId, List<PriceLine> lines) {
        Map<String, BigDecimal> minimums = mongo.find(by("promotionId", promotionId), PromotionProductEntity.class)
                .stream().collect(Collectors.toMap(PromotionProductEntity::getProductId,
                        row -> minimum(row.getMinimumQuantity())));
        Map<String, BigDecimal> totals = lines.stream().collect(Collectors.toMap(
                PriceLine::productId, PriceLine::quantity, BigDecimal::add));
        return lines.stream().filter(line -> qualifies(line.productId(), minimums, totals))
                .map(line -> new Part(line.variantId(), null, line.quantity(),
                        line.unitPrice().multiply(line.quantity()))).toList();
    }

    private List<Part> eligibleBatches(String promotionId, List<PriceLine> lines, Instant now) {
        Map<String, BigDecimal> minimums = mongo.find(by("promotionId", promotionId), PromotionBatchEntity.class)
                .stream().collect(Collectors.toMap(PromotionBatchEntity::getBatchId,
                        row -> minimum(row.getMinimumQuantity())));
        if (minimums.isEmpty()) {
            return List.of();
        }
        Map<String, PriceLine> prices = lines.stream().collect(Collectors.toMap(PriceLine::variantId, Function.identity()));
        List<OrderInventory.Demand> demands = lines.stream().map(line ->
                new OrderInventory.Demand(line.variantId(), line.variantId(), line.quantity())).toList();
        List<OrderInventory.PlannedBatch> plan = inventory.preview(demands, now);
        Map<String, BigDecimal> totals = plan.stream().collect(Collectors.toMap(
                OrderInventory.PlannedBatch::batchId, OrderInventory.PlannedBatch::quantity, BigDecimal::add));
        return plan.stream().filter(pick -> qualifies(pick.batchId(), minimums, totals))
                .map(pick -> new Part(pick.lineId(), pick.batchId(), pick.quantity(),
                        prices.get(pick.lineId()).unitPrice().multiply(pick.quantity()))).toList();
    }

    private static boolean qualifies(String id, Map<String, BigDecimal> minimums, Map<String, BigDecimal> totals) {
        BigDecimal minimum = minimums.get(id);
        return minimum != null && totals.getOrDefault(id, BigDecimal.ZERO).compareTo(minimum) >= 0;
    }

    private static BigDecimal minimum(BigDecimal amount) {
        return amount == null ? BigDecimal.ONE : amount;
    }

    /** Called within the checkout transaction after preview has read the current MongoDB snapshot. */
    @Override
    public List<OrderInventory.PlannedBatch> claim(OrderEntity order, List<OrderDetailEntity> lines,
                                                  Quote quote, Instant now) {
        PromotionEntity promotion = quote.promotion();
        CustomerVoucherEntity voucher = quote.voucher();
        mongo.updateFirst(by("_id", promotion.getId()), new Update().inc("usageCount", 1)
                .set("updatedAt", now), PromotionEntity.class);
        if (voucher != null) {
            mongo.updateFirst(by("_id", voucher.getId()), new Update().inc("usageCount", 1)
                    .set("lastUsedAt", now).set("updatedAt", now), CustomerVoucherEntity.class);
        }
        OrderPromotionEntity applied = new OrderPromotionEntity();
        applied.setOrderId(order.getId());
        applied.setCustomerId(order.getCustomerId());
        applied.setPromotionId(promotion.getId());
        applied.setCustomerVoucherId(voucher == null ? null : voucher.getId());
        applied.setPromotionCode(quote.code());
        applied.setPromotionName(promotion.getName());
        applied.setTarget(promotion.getTarget());
        applied.setDiscountType(promotion.getDiscountType());
        applied.setDiscountValue(promotion.getDiscountValue());
        applied.setEligibleAmount(quote.eligibleAmount());
        applied.setAppliedLines(appliedLines(lines, quote));
        applied.setDiscountAmount(quote.discount());
        applied.setAppliedAt(now);
        applied.setCreatedAt(now);
        applied.setUpdatedAt(now);
        mongo.insert(applied);
        return batchRequirements(applied.getAppliedLines());
    }

    private static List<OrderPromotionEntity.AppliedLine> appliedLines(List<OrderDetailEntity> lines, Quote quote) {
        Map<String, String> lineIds = lines.stream().collect(Collectors.toMap(
                OrderDetailEntity::getVariantId, OrderDetailEntity::getId));
        List<OrderPromotionEntity.AppliedLine> applied = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        BigDecimal allocated = BigDecimal.ZERO;
        for (Part part : quote.parts()) {
            running = running.add(part.amount());
            BigDecimal share = running.multiply(quote.discount()).divide(quote.eligibleAmount(), 0, RoundingMode.DOWN);
            applied.add(new OrderPromotionEntity.AppliedLine(lineIds.get(part.variantId()), part.batchId(),
                    part.quantity(), share.subtract(allocated)));
            allocated = share;
        }
        return applied;
    }

    @Override
    public List<OrderInventory.PlannedBatch> requiredBatches(String orderId) {
        OrderPromotionEntity applied = mongo.findOne(Query.query(Criteria.where("orderId").is(orderId)
                .and("releasedAt").is(null)), OrderPromotionEntity.class);
        if (applied == null || applied.getAppliedLines() == null) {
            return List.of();
        }
        return batchRequirements(applied.getAppliedLines());
    }

    private static List<OrderInventory.PlannedBatch> batchRequirements(List<OrderPromotionEntity.AppliedLine> lines) {
        return lines.stream().filter(line -> line.batchId() != null)
                .map(line -> new OrderInventory.PlannedBatch(line.orderDetailId(), line.batchId(),
                        line.eligibleQuantity())).toList();
    }

    /** Called within the same transaction that releases stock and cancels the order. */
    @Override
    public void release(String orderId, Instant now) {
        OrderPromotionEntity applied = mongo.findOne(Query.query(Criteria.where("orderId").is(orderId)
                .and("releasedAt").is(null)), OrderPromotionEntity.class);
        if (applied == null) {
            return;
        }
        mongo.updateFirst(used(applied.getPromotionId()), new Update().inc("usageCount", -1)
                .set("updatedAt", now), PromotionEntity.class);
        if (applied.getCustomerVoucherId() != null) {
            mongo.updateFirst(used(applied.getCustomerVoucherId()), new Update().inc("usageCount", -1)
                    .set("updatedAt", now), CustomerVoucherEntity.class);
        }
        mongo.updateFirst(by("_id", applied.getId()), new Update().set("releasedAt", now)
                .set("updatedAt", now), OrderPromotionEntity.class);
    }

    @Override
    public String appliedCode(String orderId) {
        OrderPromotionEntity applied = mongo.findOne(by("orderId", orderId), OrderPromotionEntity.class);
        return applied == null ? null : applied.getPromotionCode();
    }

    static boolean activeAt(PromotionEntity promotion, Instant now) {
        return AuthStore.ACTIVE.equals(promotion.getStatus())
                && (promotion.getStartsAt() == null || !now.isBefore(promotion.getStartsAt()))
                && (promotion.getEndsAt() == null || now.isBefore(promotion.getEndsAt()));
    }

    static boolean usable(CustomerVoucherEntity voucher, Instant now) {
        return AuthStore.ACTIVE.equals(voucher.getStatus())
                && (voucher.getExpiresAt() == null || now.isBefore(voucher.getExpiresAt()))
                && !exhausted(voucher.getUsageLimit(), voucher.getUsageCount());
    }

    static boolean exhausted(Integer limit, long count) {
        return limit != null && count >= limit;
    }

    public static String normalize(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private static Query used(String id) {
        return Query.query(Criteria.where("_id").is(id).and("usageCount").gt(0));
    }

    private static Query by(String field, Object value) {
        return Query.query(Criteria.where(field).is(value));
    }
}
