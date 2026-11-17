package vn.ducthe.organic.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.inventory.BatchAvailability;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.InventoryResponses.Batch;
import vn.ducthe.organic.dto.response.InventoryResponses.VariantStock;
import vn.ducthe.organic.entity.ProductBatchEntity;
import vn.ducthe.organic.entity.ProductVariantEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.service.InventoryService;

@Service
public class InventoryServiceImpl implements InventoryService {
    private final MongoDocumentRepository mongo;
    private final int warningDays;

    public InventoryServiceImpl(MongoDocumentRepository mongo, @Value("${app.inventory.expiry-warning-days:7}") int warningDays) {
        this.mongo = mongo;
        this.warningDays = warningDays;
        if (warningDays < 0 || warningDays > 365) {
            throw new IllegalArgumentException("app.inventory.expiry-warning-days must be between 0 and 365");
        }
    }

    @Override
    public PageData<Batch> batches(String q, String variantId, BatchStatus status, Expiry expiry, boolean inStockOnly,
                            int page, int size) {
        LocalDate today = BatchAvailability.shopDate(Instant.now());
        Query filter = new Query();
        if (variantId != null && !variantId.isBlank()) {
            filter.addCriteria(Criteria.where("variantId").is(variantId));
        }
        if (status != null) {
            filter.addCriteria(Criteria.where("status").is(status.name()));
        }
        if (expiry != null) {
            filter.addCriteria(expiryFilter(expiry, today));
        }
        if (inStockOnly) {
            filter.addCriteria(Criteria.where("quantityOnHand").gt(Decimal128.POSITIVE_ZERO));
        }
        if (q != null && !q.isBlank()) {
            Pattern search = literal(q);
            filter.addCriteria(new Criteria().orOperator(Criteria.where("batchCode").regex(search),
                    Criteria.where("lotCode").regex(search)));
        }
        var pageable = PageRequest.of(page - 1, size, Sort.by("expiryDate", "_id"));
        long total = mongo.count(filter, ProductBatchEntity.class);
        List<ProductBatchEntity> rows = mongo.find(Query.of(filter).with(pageable), ProductBatchEntity.class);
        List<String> ids = rows.stream().map(ProductBatchEntity::getVariantId).distinct().toList();
        Map<String, ProductVariantEntity> variants = mongo.find(Query.query(Criteria.where("_id").in(ids)),
                ProductVariantEntity.class).stream()
                .collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
        List<Batch> result = rows.stream().map(row -> batch(row, variants.get(row.getVariantId()), today)).toList();
        return PageData.from(new PageImpl<>(result, pageable, total));
    }

    @Override
    public Batch detail(String id) {
        ProductBatchEntity row = get(id);
        return batch(row, variant(row), BatchAvailability.shopDate(Instant.now()));
    }

    @Override
    public Batch status(String id, String accountId, BatchStatus status, String reason) {
        Instant now = Instant.now();
        LocalDate today = BatchAvailability.shopDate(now);
        String note = SupplierServiceImpl.clean(reason);
        if (status == BatchStatus.INACTIVE && note == null) {
            throw new ApiException(ApiError.BATCH_STATUS_REASON_REQUIRED);
        }
        Criteria filter = Criteria.where("_id").is(id);
        if (status == BatchStatus.ACTIVE) {
            filter.and("expiryDate").gte(today);
        }
        ProductBatchEntity row = mongo.findAndModify(Query.query(filter), new Update().set("status", status.name())
                .set("statusChangedByAccountId", accountId).set("statusChangedAt", now)
                .set("statusReason", note).set("updatedAt", now), FindAndModifyOptions.options().returnNew(true),
                ProductBatchEntity.class);
        if (row == null) {
            get(id);
            throw new ApiException(ApiError.BATCH_NOT_SELLABLE);
        }
        return batch(row, variant(row), today);
    }

    @Override
    public PageData<Batch> expiryReport(String variantId, boolean expired, int page, int size) {
        return batches(null, variantId, null, expired ? Expiry.EXPIRED : Expiry.EXPIRING_SOON, true, page, size);
    }

    @Override
    public PageData<VariantStock> skuStock(String q, boolean lowStockOnly, int page, int size) {
        var pageable = PageRequest.of(page - 1, size);
        Document search = new Document();
        if (q != null && !q.isBlank()) {
            Pattern match = literal(q);
            search.append("$or", List.of(new Document("sku", match), new Document("name", match),
                    new Document("barcode", match)));
        }
        if (lowStockOnly) {
            search.append("status", AuthStore.ACTIVE);
        }
        Object today = mongo.toMongoValue(BatchAvailability.shopDate(Instant.now()));
        Document eligible = new Document("$and", List.of(
                new Document("$eq", List.of("$status", AuthStore.ACTIVE)),
                new Document("$gte", List.of("$expiryDate", today))));
        Document quantity = new Document("$cond", List.of(eligible, BatchAvailability.unreservedExpression(),
                Decimal128.POSITIVE_ZERO));
        Document totals = new Document("_id", null)
                .append("quantityOnHand", new Document("$sum", BatchAvailability.nonNull("$quantityOnHand")))
                .append("quantityReserved", new Document("$sum", BatchAvailability.nonNull("$quantityReserved")))
                .append("quantityAvailable", new Document("$sum", quantity));
        Document lookup = new Document("from", mongo.getCollectionName(ProductBatchEntity.class))
                .append("localField", "variantRef").append("foreignField", "variantId")
                .append("pipeline", List.of(new Document("$group", totals))).append("as", "stock");

        Document values = new Document("quantityOnHand", firstOrZero("$stock.quantityOnHand"))
                .append("quantityReserved", firstOrZero("$stock.quantityReserved"))
                .append("quantityAvailable", firstOrZero("$stock.quantityAvailable"))
                .append("reorderLevel", BatchAvailability.nonNull("$reorderLevel"));
        Document lowStock = new Document("$and", List.of(
                new Document("$eq", List.of("$status", AuthStore.ACTIVE)),
                new Document("$lte", List.of("$quantityAvailable", "$reorderLevel"))));
        Document fields = new Document("_id", 0).append("variantId", "$variantRef")
                .append("productId", 1).append("sku", 1).append("name", 1).append("status", 1)
                .append("quantityOnHand", 1).append("quantityReserved", 1).append("quantityAvailable", 1)
                .append("reorderLevel", 1).append("lowStock", 1);
        Document facet = new Document("items", List.of(new Document("$sort", new Document("sku", 1).append("_id", 1)),
                new Document("$skip", pageable.getOffset()), new Document("$limit", size),
                new Document("$project", fields))).append("count", List.of(new Document("$count", "total")));
        Aggregation query = Aggregation.newAggregation(
                context -> new Document("$match", search),
                context -> new Document("$set", new Document("variantRef", new Document("$toString", "$_id"))),
                context -> new Document("$lookup", lookup),
                context -> new Document("$set", values),
                context -> new Document("$set", new Document("lowStock", lowStock)),
                context -> new Document("$match", lowStockOnly ? new Document("lowStock", true) : new Document()),
                context -> new Document("$facet", facet));
        Document result = mongo.aggregate(query, mongo.getCollectionName(ProductVariantEntity.class), Document.class)
                .getUniqueMappedResult();
        if (result == null) {
            return PageData.from(new PageImpl<>(List.of(), pageable, 0));
        }
        List<Document> count = result.getList("count", Document.class);
        long total = count.isEmpty() ? 0 : count.getFirst().get("total", Number.class).longValue();
        List<VariantStock> items = result.getList("items", Document.class).stream().map(InventoryServiceImpl::stock).toList();
        return PageData.from(new PageImpl<>(items, pageable, total));
    }

    private Criteria expiryFilter(Expiry expiry, LocalDate today) {
        Criteria date = Criteria.where("expiryDate");
        return switch (expiry) {
            case EXPIRED -> date.lt(today);
            case EXPIRING_SOON -> date.gte(today).lte(today.plusDays(warningDays));
            case FRESH -> date.gt(today.plusDays(warningDays));
            case UNKNOWN -> date.is(null);
        };
    }

    private Batch batch(ProductBatchEntity row, ProductVariantEntity variant, LocalDate today) {
        LocalDate date = row.getExpiryDate();
        Long days = date == null ? null : ChronoUnit.DAYS.between(today, date);
        Expiry expiry = days == null ? Expiry.UNKNOWN : days < 0 ? Expiry.EXPIRED
                : days <= warningDays ? Expiry.EXPIRING_SOON : Expiry.FRESH;
        BigDecimal available = BatchAvailability.isSellable(row, today)
                ? BatchAvailability.unreserved(row) : BigDecimal.ZERO;
        return new Batch(row.getId(), row.getVariantId(), variant == null ? null : variant.getSku(),
                variant == null ? null : variant.getName(), row.getBatchCode(), row.getLotCode(),
                row.getManufactureDate(), date, row.getOriginDescription(), row.getStatus(), expiry, days,
                BatchAvailability.amount(row.getQuantityOnHand()), BatchAvailability.amount(row.getQuantityReserved()),
                available, row.getStatusReason(), row.getStatusChangedAt(), row.getStatusChangedByAccountId());
    }

    private ProductBatchEntity get(String id) {
        ProductBatchEntity row = mongo.findById(id, ProductBatchEntity.class);
        if (row == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private ProductVariantEntity variant(ProductBatchEntity batch) {
        return batch.getVariantId() == null ? null : mongo.findById(batch.getVariantId(), ProductVariantEntity.class);
    }

    private static Pattern literal(String text) {
        return Pattern.compile(Pattern.quote(text.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static Document firstOrZero(String field) {
        return new Document("$ifNull", List.of(new Document("$arrayElemAt", List.of(field, 0)), Decimal128.POSITIVE_ZERO));
    }

    private static VariantStock stock(Document row) {
        return new VariantStock(row.getString("variantId"), row.getString("productId"), row.getString("sku"),
                row.getString("name"), row.getString("status"), decimal(row, "quantityOnHand"),
                decimal(row, "quantityReserved"), decimal(row, "quantityAvailable"), decimal(row, "reorderLevel"),
                row.getBoolean("lowStock", false));
    }

    private static BigDecimal decimal(Document row, String field) {
        Object value = row.get(field);
        return value instanceof Decimal128 decimal ? decimal.bigDecimalValue() : new BigDecimal(value.toString());
    }
}
