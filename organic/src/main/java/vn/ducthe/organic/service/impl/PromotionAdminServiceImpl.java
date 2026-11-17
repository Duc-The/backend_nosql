package vn.ducthe.organic.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bson.types.Decimal128;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.PromotionAdminPayloads.Input;
import vn.ducthe.organic.dto.request.PromotionAdminPayloads.Status;
import vn.ducthe.organic.dto.request.PromotionAdminPayloads.TargetRule;
import vn.ducthe.organic.dto.request.PromotionAdminPayloads.VoucherInput;
import vn.ducthe.organic.dto.response.PromotionAdminResponses.Detail;
import vn.ducthe.organic.entity.BaseEntity;
import vn.ducthe.organic.entity.CustomerEntity;
import vn.ducthe.organic.entity.CustomerVoucherEntity;
import vn.ducthe.organic.entity.ProductBatchEntity;
import vn.ducthe.organic.entity.ProductEntity;
import vn.ducthe.organic.entity.PromotionBatchEntity;
import vn.ducthe.organic.entity.PromotionEntity;
import vn.ducthe.organic.entity.PromotionEntity.Target;
import vn.ducthe.organic.entity.PromotionProductEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.repository.OrderTransactions;
import vn.ducthe.organic.service.PromotionAdminService;

@Service
public class PromotionAdminServiceImpl implements PromotionAdminService {
    private final MongoDocumentRepository mongo;
    private final OrderTransactions transactions;

    public PromotionAdminServiceImpl(MongoDocumentRepository mongo, OrderTransactions transactions) {
        this.mongo = mongo;
        this.transactions = transactions;
    }

    @Override
    public PageData<PromotionEntity> list(String q, Target target, Status status, int page, int size) {
        Query filter = new Query();
        if (target != null) {
            filter.addCriteria(Criteria.where("target").is(target));
        }
        if (status != null) {
            filter.addCriteria(Criteria.where("status").is(status.name()));
        }
        if (q != null && !q.isBlank()) {
            Pattern search = Pattern.compile(Pattern.quote(q.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            filter.addCriteria(new Criteria().orOperator(Criteria.where("promotionCode").regex(search),
                    Criteria.where("name").regex(search)));
        }
        var pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "_id"));
        long total = mongo.count(filter, PromotionEntity.class);
        List<PromotionEntity> rows = mongo.find(Query.of(filter).with(pageable), PromotionEntity.class);
        return PageData.from(new PageImpl<>(rows, pageable, total));
    }

    @Override
    public Detail detail(String id) {
        PromotionEntity promotion = get(id);
        List<TargetRule> targets = switch (promotion.getTarget()) {
            case ORDER -> List.of();
            case PRODUCT -> mongo.find(by("promotionId", id).with(Sort.by("productId")), PromotionProductEntity.class)
                    .stream().map(row -> new TargetRule(row.getProductId(), row.getMinimumQuantity())).toList();
            case BATCH -> mongo.find(by("promotionId", id).with(Sort.by("batchId")), PromotionBatchEntity.class)
                    .stream().map(row -> new TargetRule(row.getBatchId(), row.getMinimumQuantity())).toList();
        };
        return new Detail(promotion, targets);
    }

    @Override
    public Detail create(Input input) {
        List<TargetRule> targets = validate(input);
        return transactions.execute(() -> {
            requireTargets(input.target(), targets);
            Instant now = Instant.now();
            PromotionEntity promotion = new PromotionEntity();
            stamp(promotion, now);
            promotion.setPromotionCode(PromotionServiceImpl.normalize(input.promotionCode()));
            promotion.setName(input.name().strip());
            promotion.setDescription(clean(input.description()));
            promotion.setAudience(input.audience());
            promotion.setTarget(input.target());
            promotion.setDiscountType(input.discountType());
            promotion.setDiscountValue(input.discountValue());
            promotion.setMaxDiscountAmount(input.maxDiscountAmount());
            promotion.setMinOrderValue(input.minOrderValue());
            promotion.setStartsAt(input.startsAt());
            promotion.setEndsAt(input.endsAt());
            promotion.setUsageLimit(input.usageLimit());
            promotion.setPerCustomerLimit(input.perCustomerLimit());
            promotion.setStatus(input.status().name());
            mongo.insert(promotion);
            insertTargets(promotion, targets, now);
            return new Detail(promotion, targets);
        });
    }

    @Override
    public Detail edit(String id, Input input) {
        List<TargetRule> targets = validate(input);
        return transactions.execute(() -> {
            PromotionEntity current = editable(id);
            requireTargets(input.target(), targets);
            if (input.usageLimit() != null && input.usageLimit() < current.getUsageCount()) {
                throw new ApiException(ApiError.PROMOTION_LIMIT_BELOW_USAGE);
            }
            if (current.getAudience() != input.audience()
                    && mongo.exists(by("promotionId", id), CustomerVoucherEntity.class)) {
                throw new ApiException(ApiError.PROMOTION_RULE_INVALID);
            }
            Instant now = Instant.now();
            PromotionEntity changed = update(id, new Update()
                    .set("promotionCode", PromotionServiceImpl.normalize(input.promotionCode()))
                    .set("name", input.name().strip()).set("description", clean(input.description()))
                    .set("audience", input.audience()).set("target", input.target())
                    .set("discountType", input.discountType()).set("discountValue", decimal(input.discountValue()))
                    .set("maxDiscountAmount", decimal(input.maxDiscountAmount()))
                    .set("minOrderValue", decimal(input.minOrderValue()))
                    .set("startsAt", input.startsAt()).set("endsAt", input.endsAt()).set("usageLimit", input.usageLimit())
                    .set("perCustomerLimit", input.perCustomerLimit()).set("status", input.status().name()), now);
            replaceTargets(changed, targets, now);
            return new Detail(changed, targets);
        });
    }

    @Override
    public Detail status(String id, Status status) {
        editable(id);
        update(id, new Update().set("status", status.name()), Instant.now());
        return detail(id);
    }

    @Override
    public CustomerVoucherEntity issueVoucher(String id, VoucherInput input) {
        return transactions.execute(() -> {
            PromotionEntity promotion = editable(id);
            Instant now = Instant.now();
            Instant expires = input.expiresAt() == null ? promotion.getEndsAt() : input.expiresAt();
            if (promotion.getAudience() != PromotionEntity.Audience.ASSIGNED_CUSTOMERS
                    || !AuthStore.ACTIVE.equals(promotion.getStatus()) || expires == null || !expires.isAfter(now)
                    || promotion.getStartsAt() != null && !expires.isAfter(promotion.getStartsAt())
                    || promotion.getEndsAt() != null && expires.isAfter(promotion.getEndsAt())
                    || PromotionServiceImpl.exhausted(promotion.getUsageLimit(), promotion.getUsageCount())) {
                throw new ApiException(ApiError.PROMOTION_RULE_INVALID);
            }
            CustomerEntity customer = mongo.findById(input.customerId(), CustomerEntity.class);
            if (customer == null || !AuthStore.ACTIVE.equals(customer.getStatus())) {
                throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
            }
            CustomerVoucherEntity voucher = new CustomerVoucherEntity();
            stamp(voucher, now);
            voucher.setCustomerId(customer.getId());
            voucher.setPromotionId(id);
            voucher.setVoucherCode("VC-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT));
            voucher.setStatus(AuthStore.ACTIVE);
            voucher.setUsageLimit(input.usageLimit() == null ? 1 : input.usageLimit());
            voucher.setIssuedAt(now);
            voucher.setExpiresAt(expires);
            return mongo.insert(voucher);
        });
    }

    @Override
    public PageData<CustomerVoucherEntity> vouchers(String id, int page, int size) {
        get(id);
        Query filter = by("promotionId", id);
        var pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "issuedAt", "_id"));
        long total = mongo.count(filter, CustomerVoucherEntity.class);
        List<CustomerVoucherEntity> rows = mongo.find(Query.of(filter).with(pageable), CustomerVoucherEntity.class);
        return PageData.from(new PageImpl<>(rows, pageable, total));
    }

    private PromotionEntity update(String id, Update change, Instant now) {
        PromotionEntity row = mongo.findAndModify(by("_id", id), change.set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true), PromotionEntity.class);
        if (row == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private PromotionEntity editable(String id) {
        PromotionEntity row = get(id);
        if (id.startsWith("loyalty-")) {
            throw new ApiException(ApiError.PROMOTION_MANAGED_BY_LOYALTY);
        }
        return row;
    }

    private PromotionEntity get(String id) {
        PromotionEntity row = mongo.findById(id, PromotionEntity.class);
        if (row == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private static List<TargetRule> validate(Input input) {
        String code = PromotionServiceImpl.normalize(input.promotionCode());
        List<TargetRule> targets = input.targets() == null ? List.of() : input.targets();
        boolean invalidValue = input.discountType() == PromotionEntity.DiscountType.PERCENTAGE
                ? input.discountValue().compareTo(BigDecimal.valueOf(100)) > 0
                : input.discountValue().stripTrailingZeros().scale() > 0;
        if (code.startsWith("VC-") || code.startsWith("DIEM-") || code.startsWith("LOYALTY-")
                || !input.startsAt().isBefore(input.endsAt()) || invalidValue
                || (input.target() == Target.ORDER) != targets.isEmpty()
                || targets.stream().map(TargetRule::id).distinct().count() != targets.size()
                || input.usageLimit() != null && input.perCustomerLimit() != null
                        && input.perCustomerLimit() > input.usageLimit()) {
            throw new ApiException(ApiError.PROMOTION_RULE_INVALID);
        }
        return targets.stream().map(rule -> new TargetRule(rule.id(),
                rule.minimumQuantity() == null ? BigDecimal.ONE : rule.minimumQuantity())).toList();
    }

    private void requireTargets(Target target, List<TargetRule> targets) {
        if (target == Target.ORDER) {
            return;
        }
        var ids = targets.stream().map(TargetRule::id).collect(Collectors.toSet());
        Class<?> type = target == Target.PRODUCT ? ProductEntity.class : ProductBatchEntity.class;
        if (mongo.count(Query.query(Criteria.where("_id").in(ids)), type) != ids.size()) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
    }

    private void insertTargets(PromotionEntity promotion, List<TargetRule> targets, Instant now) {
        if (promotion.getTarget() == Target.PRODUCT) {
            List<PromotionProductEntity> rows = targets.stream()
                    .map(rule -> productRule(promotion.getId(), rule, now)).toList();
            mongo.insert(rows, PromotionProductEntity.class);
        } else if (promotion.getTarget() == Target.BATCH) {
            List<PromotionBatchEntity> rows = targets.stream()
                    .map(rule -> batchRule(promotion.getId(), rule, now)).toList();
            mongo.insert(rows, PromotionBatchEntity.class);
        }
    }

    private void replaceTargets(PromotionEntity promotion, List<TargetRule> targets, Instant now) {
        String id = promotion.getId();
        var selected = targets.stream().map(TargetRule::id).toList();
        if (promotion.getTarget() != Target.PRODUCT) {
            mongo.remove(by("promotionId", id), PromotionProductEntity.class);
        } else {
            mongo.remove(Query.query(Criteria.where("promotionId").is(id).and("productId").nin(selected)),
                    PromotionProductEntity.class);
            upsertTargets(id, "productId", targets, now, PromotionProductEntity.class);
        }
        if (promotion.getTarget() != Target.BATCH) {
            mongo.remove(by("promotionId", id), PromotionBatchEntity.class);
        } else {
            mongo.remove(Query.query(Criteria.where("promotionId").is(id).and("batchId").nin(selected)),
                    PromotionBatchEntity.class);
            upsertTargets(id, "batchId", targets, now, PromotionBatchEntity.class);
        }
    }

    private void upsertTargets(String promotionId, String key, List<TargetRule> rules, Instant now, Class<?> type) {
        var changes = mongo.bulkOps(BulkMode.ORDERED, type);
        rules.forEach(rule -> changes.upsert(Query.query(Criteria.where("promotionId").is(promotionId).and(key).is(rule.id())),
                new Update().setOnInsert("createdAt", now).set("updatedAt", now)
                        .set("minimumQuantity", decimal(rule.minimumQuantity()))));
        changes.execute();
    }

    private static PromotionProductEntity productRule(String id, TargetRule rule, Instant now) {
        PromotionProductEntity row = new PromotionProductEntity();
        stamp(row, now);
        row.setPromotionId(id);
        row.setProductId(rule.id());
        row.setMinimumQuantity(rule.minimumQuantity());
        return row;
    }

    private static PromotionBatchEntity batchRule(String id, TargetRule rule, Instant now) {
        PromotionBatchEntity row = new PromotionBatchEntity();
        stamp(row, now);
        row.setPromotionId(id);
        row.setBatchId(rule.id());
        row.setMinimumQuantity(rule.minimumQuantity());
        return row;
    }

    private static Query by(String field, Object value) {
        return Query.query(Criteria.where(field).is(value));
    }

    private static Decimal128 decimal(BigDecimal amount) {
        return amount == null ? null : new Decimal128(amount);
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static void stamp(BaseEntity row, Instant now) {
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
    }
}
