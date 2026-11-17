package vn.ducthe.organic.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.LoyaltyResponses.Balance;
import vn.ducthe.organic.dto.response.LoyaltyResponses.Offer;
import vn.ducthe.organic.dto.response.LoyaltyResponses.PointEntry;
import vn.ducthe.organic.dto.response.LoyaltyResponses.Voucher;
import vn.ducthe.organic.entity.BaseEntity;
import vn.ducthe.organic.entity.CustomerEntity;
import vn.ducthe.organic.entity.CustomerPointTransactionEntity;
import vn.ducthe.organic.entity.CustomerPointTransactionEntity.Type;
import vn.ducthe.organic.entity.CustomerVoucherEntity;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.PromotionEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.repository.OrderTransactions;
import vn.ducthe.organic.service.LoyaltyService;

@Service
public class LoyaltyServiceImpl implements LoyaltyService {
    private static final Logger log = LoggerFactory.getLogger(LoyaltyService.class);
    private final MongoDocumentRepository mongo;
    private final AuthStore auth;
    private final OrderTransactions transactions;
    private final BigDecimal earnSpend;
    private final long redeemPoints;
    private final BigDecimal voucherValue;
    private final BigDecimal voucherMinimum;
    private final Duration voucherTtl;
    private final String rewardPromotionId;

    public LoyaltyServiceImpl(MongoDocumentRepository mongo, AuthStore auth, OrderTransactions transactions,
            @Value("${app.loyalty.earn-spend:10000}") long earnSpend,
            @Value("${app.loyalty.redeem-points:100}") long redeemPoints,
            @Value("${app.loyalty.voucher-value:20000}") long voucherValue,
            @Value("${app.loyalty.voucher-minimum:100000}") long voucherMinimum,
            @Value("${app.loyalty.voucher-ttl:P30D}") Duration voucherTtl) {
        if (earnSpend <= 0 || redeemPoints <= 0 || voucherValue <= 0 || voucherMinimum < voucherValue
                || voucherTtl.isNegative() || voucherTtl.isZero()) {
            throw new IllegalArgumentException("Invalid app.loyalty configuration");
        }
        this.mongo = mongo;
        this.auth = auth;
        this.transactions = transactions;
        this.earnSpend = BigDecimal.valueOf(earnSpend);
        this.redeemPoints = redeemPoints;
        this.voucherValue = BigDecimal.valueOf(voucherValue);
        this.voucherMinimum = BigDecimal.valueOf(voucherMinimum);
        this.voucherTtl = voucherTtl;
        this.rewardPromotionId = "loyalty-" + stableId(voucherValue + ":" + voucherMinimum);
    }

    @Override
    public Balance balance(String accountId) {
        return new Balance(auth.customer(accountId).getRewardPoints(), earnSpend,
                new Offer(redeemPoints, voucherValue, voucherMinimum, voucherTtl.toSeconds()));
    }

    @Override
    public PageData<PointEntry> history(String accountId, int page, int size) {
        String customerId = auth.customer(accountId).getId();
        var pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "occurredAt", "_id"));
        Query filter = by("customerId", customerId);
        long total = mongo.count(filter, CustomerPointTransactionEntity.class);
        List<PointEntry> items = mongo.find(Query.of(filter).with(pageable), CustomerPointTransactionEntity.class)
                .stream().map(row -> new PointEntry(row.getId(), row.getOrderId(), row.getCustomerVoucherId(),
                        row.getType(), row.getPointsDelta(), row.getBalanceAfter(), row.getReason(), row.getOccurredAt()))
                .toList();
        return PageData.from(new PageImpl<>(items, pageable, total));
    }

    @Override
    public PageData<Voucher> vouchers(String accountId, int page, int size) {
        String customerId = auth.customer(accountId).getId();
        var pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "issuedAt", "_id"));
        Query filter = by("customerId", customerId);
        long total = mongo.count(filter, CustomerVoucherEntity.class);
        List<CustomerVoucherEntity> items = mongo.find(Query.of(filter).with(pageable), CustomerVoucherEntity.class);
        List<String> ids = items.stream().map(CustomerVoucherEntity::getPromotionId).distinct().toList();
        Map<String, PromotionEntity> promotions = ids.isEmpty() ? Map.of() : mongo.find(
                Query.query(Criteria.where("_id").in(ids)), PromotionEntity.class).stream()
                .collect(Collectors.toMap(PromotionEntity::getId, Function.identity()));
        Instant now = Instant.now();
        List<Voucher> views = items.stream().map(voucher -> view(voucher, promotions.get(voucher.getPromotionId()), now))
                .toList();
        return PageData.from(new PageImpl<>(views, pageable, total));
    }

    @Override
    public Redemption redeem(String accountId, String key) {
        String customerId = auth.customer(accountId).getId();
        String ledgerKey = "redeem:" + key;
        String voucherId = "loyalty-voucher-" + stableId(customerId + ":" + ledgerKey);
        try {
            return transactions.execute(() -> redeem(customerId, accountId, ledgerKey, voucherId));
        } catch (DuplicateKeyException exception) {
            // A concurrent request may have issued this voucher or initialized the shared reward promotion.
            return transactions.execute(() -> redeem(customerId, accountId, ledgerKey, voucherId));
        }
    }

    private Redemption redeem(String customerId, String accountId, String key, String voucherId) {
        Instant now = Instant.now();
        CustomerVoucherEntity existing = mongo.findById(voucherId, CustomerVoucherEntity.class);
        if (existing != null) {
            return new Redemption(view(existing, mongo.findById(existing.getPromotionId(), PromotionEntity.class), now), false);
        }
        PromotionEntity promotion = rewardPromotion(now);
        CustomerEntity customer = changeBalance(customerId, -redeemPoints, now);
        CustomerVoucherEntity voucher = new CustomerVoucherEntity();
        voucher.setId(voucherId);
        stamp(voucher, now);
        voucher.setCustomerId(customerId);
        voucher.setPromotionId(promotion.getId());
        voucher.setVoucherCode("DIEM-" + voucherId.substring("loyalty-voucher-".length()).replace("-", "")
                .toUpperCase(Locale.ROOT));
        voucher.setStatus(AuthStore.ACTIVE);
        voucher.setUsageLimit(1);
        voucher.setIssuedAt(now);
        voucher.setExpiresAt(now.plus(voucherTtl));
        mongo.insert(voucher);

        var entry = entry(customer, key, Type.REDEEM, -redeemPoints, "Đổi điểm lấy voucher cá nhân", now);
        entry.setRecordedByAccountId(accountId);
        entry.setCustomerVoucherId(voucherId);
        mongo.insert(entry);
        return new Redemption(view(voucher, promotion, now), true);
    }

    private PromotionEntity rewardPromotion(Instant now) {
        PromotionEntity existing = mongo.findById(rewardPromotionId, PromotionEntity.class);
        if (existing != null) {
            if (!PromotionServiceImpl.activeAt(existing, now)) {
                throw new ApiException(ApiError.PROMOTION_UNAVAILABLE);
            }
            return existing;
        }
        PromotionEntity promotion = new PromotionEntity();
        promotion.setId(rewardPromotionId);
        stamp(promotion, now);
        promotion.setPromotionCode(rewardPromotionId.toUpperCase(Locale.ROOT));
        promotion.setName("Voucher đổi điểm");
        promotion.setAudience(PromotionEntity.Audience.ASSIGNED_CUSTOMERS);
        promotion.setTarget(PromotionEntity.Target.ORDER);
        promotion.setDiscountType(PromotionEntity.DiscountType.FIXED_AMOUNT);
        promotion.setDiscountValue(voucherValue);
        promotion.setMinOrderValue(voucherMinimum);
        promotion.setStatus(AuthStore.ACTIVE);
        return mongo.insert(promotion);
    }

    @Scheduled(fixedDelayString = "${app.loyalty.earn-scan-delay:PT1M}", initialDelayString = "PT1M")
    @Override
    public void awardCompletedOrders() {
        Query query = Query.query(unprocessed()).with(Sort.by("completedAt", "_id")).limit(50);
        query.fields().include("_id");
        for (OrderEntity candidate : mongo.find(query, OrderEntity.class)) {
            try {
                transactions.execute(() -> {
                    OrderEntity order = mongo.findOne(Query.query(unprocessed().and("_id").is(candidate.getId())),
                            OrderEntity.class);
                    if (order != null) {
                        award(order, Instant.now());
                    }
                    return null;
                });
            } catch (RuntimeException exception) {
                log.warn("Could not award points for order {}", candidate.getId(), exception);
            }
        }
    }

    private void award(OrderEntity order, Instant now) {
        var existing = mongo.findOne(Query.query(Criteria.where("orderId").is(order.getId()).and("type").is(Type.EARN)),
                CustomerPointTransactionEntity.class);
        BigDecimal subtotal = order.getSubtotalAmount() == null ? BigDecimal.ZERO : order.getSubtotalAmount();
        BigDecimal discount = order.getPromotionDiscount() == null ? BigDecimal.ZERO : order.getPromotionDiscount();
        long points = existing == null ? subtotal.subtract(discount).max(BigDecimal.ZERO)
                .divide(earnSpend, 0, RoundingMode.DOWN).longValueExact() : existing.getPointsDelta();
        if (existing == null && points > 0) {
            CustomerEntity customer = changeBalance(order.getCustomerId(), points, now);
            var entry = entry(customer, "earn:" + order.getId(), Type.EARN, points,
                    "Tích điểm đơn " + order.getOrderCode(), now);
            entry.setOrderId(order.getId());
            mongo.insert(entry);
        }
        mongo.updateFirst(by("_id", order.getId()), new Update().set("pointsEarned", points)
                .set("pointsProcessedAt", now).set("updatedAt", now), OrderEntity.class);
    }

    private CustomerEntity changeBalance(String customerId, long delta, Instant now) {
        Criteria filter = Criteria.where("_id").is(customerId);
        if (delta < 0) {
            filter.and("rewardPoints").gte(-delta);
        }
        CustomerEntity customer = mongo.findAndModify(Query.query(filter),
                new Update().inc("rewardPoints", delta).set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true), CustomerEntity.class);
        if (customer == null) {
            throw new ApiException(delta < 0 ? ApiError.INSUFFICIENT_POINTS : ApiError.RESOURCE_NOT_FOUND);
        }
        return customer;
    }

    private static Criteria unprocessed() {
        return Criteria.where("status").is("COMPLETED").and("paymentStatus").is("PAID")
                .and("pointsProcessedAt").is(null);
    }

    private static CustomerPointTransactionEntity entry(CustomerEntity customer, String key, Type type,
                                                        long delta, String reason, Instant now) {
        var entry = new CustomerPointTransactionEntity();
        stamp(entry, now);
        entry.setCustomerId(customer.getId());
        entry.setIdempotencyKey(key);
        entry.setType(type);
        entry.setPointsDelta(delta);
        entry.setBalanceAfter(customer.getRewardPoints());
        entry.setReason(reason);
        entry.setOccurredAt(now);
        return entry;
    }

    private static Voucher view(CustomerVoucherEntity voucher, PromotionEntity promotion, Instant now) {
        String status;
        if (PromotionServiceImpl.exhausted(voucher.getUsageLimit(), voucher.getUsageCount())) {
            status = "USED";
        } else if (voucher.getExpiresAt() != null && !now.isBefore(voucher.getExpiresAt())) {
            status = "EXPIRED";
        } else if (!PromotionServiceImpl.usable(voucher, now) || promotion == null
                || !PromotionServiceImpl.activeAt(promotion, now)
                || PromotionServiceImpl.exhausted(promotion.getUsageLimit(), promotion.getUsageCount())) {
            status = "UNAVAILABLE";
        } else {
            status = AuthStore.ACTIVE;
        }
        return new Voucher(voucher.getId(), voucher.getVoucherCode(), status, voucher.getUsageLimit(),
                voucher.getUsageCount(), voucher.getIssuedAt(), voucher.getExpiresAt(),
                promotion == null ? null : promotion.getName(),
                promotion == null ? null : promotion.getDiscountType(),
                promotion == null ? null : promotion.getDiscountValue(),
                promotion == null ? null : promotion.getMinOrderValue());
    }

    private static Query by(String field, Object value) {
        return Query.query(Criteria.where(field).is(value));
    }

    private static String stableId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void stamp(BaseEntity row, Instant now) {
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
    }
}
