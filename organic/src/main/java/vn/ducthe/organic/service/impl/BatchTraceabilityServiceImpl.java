package vn.ducthe.organic.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.BatchTraceabilityResponses.IssuedOrder;
import vn.ducthe.organic.dto.response.BatchTraceabilityResponses.Origin;
import vn.ducthe.organic.dto.response.BatchTraceabilityResponses.Supplier;
import vn.ducthe.organic.dto.response.BatchTraceabilityResponses.TracedBatch;
import vn.ducthe.organic.entity.BaseEntity;
import vn.ducthe.organic.entity.GoodsReceiptDetailEntity;
import vn.ducthe.organic.entity.GoodsReceiptEntity;
import vn.ducthe.organic.entity.OrderDetailBatchEntity;
import vn.ducthe.organic.entity.OrderDetailEntity;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.ProductBatchEntity;
import vn.ducthe.organic.entity.ProductVariantEntity;
import vn.ducthe.organic.entity.SupplierEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.service.BatchTraceabilityService;

@Service
public class BatchTraceabilityServiceImpl implements BatchTraceabilityService {
    private final MongoDocumentRepository mongo;

    public BatchTraceabilityServiceImpl(MongoDocumentRepository mongo) {
        this.mongo = mongo;
    }

    @Override
    public PageData<TracedBatch> lookup(String rawCode, int page, int size) {
        String code = rawCode.strip();
        Query filter = Query.query(new Criteria().orOperator(Criteria.where("batchCode").is(code),
                Criteria.where("lotCode").is(code)));
        var pageable = PageRequest.of(page - 1, size, Sort.by("batchCode", "_id"));
        long total = mongo.count(filter, ProductBatchEntity.class);
        List<ProductBatchEntity> rows = mongo.find(Query.of(filter).with(pageable), ProductBatchEntity.class);
        return PageData.from(new PageImpl<>(origins(rows), pageable, total));
    }

    @Override
    public PageData<IssuedOrder> issuedOrders(String batchId, int page, int size) {
        if (!mongo.exists(Query.query(Criteria.where("_id").is(batchId)), ProductBatchEntity.class)) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }

        var pageable = PageRequest.of(page - 1, size);
        Document match = new Document("batchId", batchId).append("status", "ISSUED");
        Document grouped = new Document("_id", "$line.orderId")
                .append("quantityIssued", new Document("$sum", "$quantity"))
                .append("firstIssuedAt", new Document("$min", "$issuedAt"))
                .append("lastIssuedAt", new Document("$max", "$issuedAt"));
        Document fields = new Document("_id", 0).append("orderCode", "$order.orderCode")
                .append("orderStatus", "$order.status").append("orderedAt", "$order.orderedAt")
                .append("shippedAt", "$order.shippedAt").append("deliveredAt", "$order.deliveredAt")
                .append("firstIssuedAt", 1).append("lastIssuedAt", 1).append("quantityIssued", 1);
        Document facet = new Document("items", List.of(
                new Document("$sort", new Document("lastIssuedAt", -1).append("_id", 1)),
                new Document("$skip", pageable.getOffset()),
                new Document("$limit", size),
                new Document("$project", fields)))
                .append("count", List.of(new Document("$count", "total")));

        Aggregation query = Aggregation.newAggregation(
                context -> new Document("$match", match),
                context -> new Document("$set", new Document("lineRef", mongoId("$orderDetailId"))),
                context -> join(OrderDetailEntity.class, "lineRef", "line"),
                context -> new Document("$unwind", "$line"),
                context -> new Document("$group", grouped),
                context -> new Document("$set", new Document("orderRef", mongoId("$_id"))),
                context -> join(OrderEntity.class, "orderRef", "order"),
                context -> new Document("$unwind", "$order"),
                context -> new Document("$facet", facet));

        Document result = mongo.aggregate(query, mongo.getCollectionName(OrderDetailBatchEntity.class), Document.class)
                .getUniqueMappedResult();
        if (result == null) {
            return PageData.from(new PageImpl<>(List.of(), pageable, 0));
        }
        List<Document> count = result.getList("count", Document.class);
        long total = count.isEmpty() ? 0 : count.getFirst().get("total", Number.class).longValue();
        List<IssuedOrder> items = result.getList("items", Document.class).stream()
                .map(BatchTraceabilityServiceImpl::issuedOrder).toList();
        return PageData.from(new PageImpl<>(items, pageable, total));
    }

    private List<TracedBatch> origins(List<ProductBatchEntity> batches) {
        if (batches.isEmpty()) {
            return List.of();
        }
        Set<String> batchIds = ids(batches, ProductBatchEntity::getId);
        List<GoodsReceiptDetailEntity> lines = mongo.find(
                Query.query(Criteria.where("batchId").in(batchIds)).with(Sort.by("goodsReceiptId", "lineNumber", "_id")),
                GoodsReceiptDetailEntity.class);
        Map<String, List<GoodsReceiptDetailEntity>> byBatch = lines.stream()
                .collect(Collectors.groupingBy(GoodsReceiptDetailEntity::getBatchId));
        Map<String, GoodsReceiptEntity> receipts = related(ids(lines, GoodsReceiptDetailEntity::getGoodsReceiptId),
                GoodsReceiptEntity.class);
        Map<String, SupplierEntity> suppliers = related(ids(receipts.values(), GoodsReceiptEntity::getSupplierId),
                SupplierEntity.class);
        Map<String, ProductVariantEntity> variants = related(ids(batches, ProductBatchEntity::getVariantId),
                ProductVariantEntity.class);

        return batches.stream().map(batch -> {
            ProductVariantEntity variant = get(variants, batch.getVariantId());
            List<Origin> sources = byBatch.getOrDefault(batch.getId(), List.of()).stream().map(line -> {
                GoodsReceiptEntity receipt = get(receipts, line.getGoodsReceiptId());
                SupplierEntity supplier = receipt == null ? null : get(suppliers, receipt.getSupplierId());
                return origin(line, receipt, supplier);
            }).toList();
            return new TracedBatch(batch.getId(), batch.getBatchCode(), batch.getLotCode(), batch.getVariantId(),
                    variant == null ? null : variant.getSku(), variant == null ? null : variant.getName(),
                    batch.getManufactureDate(), batch.getExpiryDate(), batch.getOriginDescription(), batch.getStatus(),
                    batch.getQuantityOnHand(), batch.getQuantityReserved(), sources);
        }).toList();
    }

    private <T extends BaseEntity> Map<String, T> related(Set<String> ids, Class<T> type) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mongo.find(Query.query(Criteria.where("_id").in(ids)), type).stream()
                .collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
    }

    private Document join(Class<?> target, String field, String alias) {
        return new Document("$lookup", new Document("from", mongo.getCollectionName(target))
                .append("localField", field).append("foreignField", "_id").append("as", alias));
    }

    private static Document mongoId(String expression) {
        return new Document("$convert", new Document("input", expression).append("to", "objectId")
                .append("onError", expression).append("onNull", expression));
    }

    private static <T> Set<String> ids(java.util.Collection<T> rows, Function<T, String> value) {
        return rows.stream().map(value).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> T get(Map<String, T> index, String id) {
        return id == null ? null : index.get(id);
    }

    private static Origin origin(GoodsReceiptDetailEntity line, GoodsReceiptEntity receipt, SupplierEntity supplier) {
        Supplier source = supplier == null ? null : new Supplier(supplier.getId(), supplier.getSupplierCode(),
                supplier.getName(), supplier.getContactPerson(), supplier.getPhoneNumber(), supplier.getEmail());
        return new Origin(line.getId(), line.getGoodsReceiptId(), receipt == null ? null : receipt.getReceiptCode(),
                receipt == null ? null : receipt.getStatus(), receipt == null ? null : receipt.getReceivedAt(),
                receipt == null ? null : receipt.getConfirmedAt(), receipt == null ? null : receipt.getSupplierId(),
                source, line.getQuantityReceived(), line.getQuantityRejected());
    }

    private static IssuedOrder issuedOrder(Document row) {
        Object value = row.get("quantityIssued");
        BigDecimal quantity = value instanceof Decimal128 decimal ? decimal.bigDecimalValue()
                : new BigDecimal(value.toString());
        return new IssuedOrder(row.getString("orderCode"), row.getString("orderStatus"), quantity,
                instant(row, "orderedAt"), instant(row, "firstIssuedAt"), instant(row, "lastIssuedAt"),
                instant(row, "shippedAt"), instant(row, "deliveredAt"));
    }

    private static Instant instant(Document row, String field) {
        Date date = row.getDate(field);
        return date == null ? null : date.toInstant();
    }
}
