package vn.ducthe.organic.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import vn.ducthe.organic.common.inventory.BatchAvailability;
import vn.ducthe.organic.entity.OrderDetailBatchEntity;
import vn.ducthe.organic.entity.OrderDetailEntity;
import vn.ducthe.organic.entity.ProductBatchEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.MongoDocumentRepository;

@Component
public class OrderInventory {
    private static final String RESERVED = "RESERVED";
    private static final String ISSUED = "ISSUED";
    private static final String RELEASED = "RELEASED";
    private final MongoDocumentRepository mongo;

    OrderInventory(MongoDocumentRepository mongo) {
        this.mongo = mongo;
    }

    void reserve(List<OrderDetailEntity> lines, List<PlannedBatch> required, Instant now) {
        LocalDate today = BatchAvailability.shopDate(now);
        List<Pick> picks = plan(demands(lines), Map.of(), pinned(required), today);
        List<OrderDetailBatchEntity> allocations = new ArrayList<>();
        Map<String, StockChange> changes = new HashMap<>();
        for (Pick pick : picks) {
            allocations.add(allocation(pick, RESERVED, now));
            accumulate(changes, pick.batchId(), pick.quantity(), BigDecimal.ZERO);
        }
        applyStockChanges(changes, now, today);
        mongo.insert(allocations, OrderDetailBatchEntity.class);
    }

    void release(List<OrderDetailEntity> lines, Instant now) {
        List<OrderDetailBatchEntity> allocations = allocations(lines, RESERVED);
        Map<String, StockChange> changes = new HashMap<>();
        for (OrderDetailBatchEntity row : allocations) {
            accumulate(changes, row.getBatchId(), row.getQuantity().negate(), BigDecimal.ZERO);
        }
        applyStockChanges(changes, now, BatchAvailability.shopDate(now));
        if (!allocations.isEmpty()) {
            List<String> ids = allocations.stream().map(OrderDetailBatchEntity::getId).toList();
            mongo.updateMulti(Query.query(Criteria.where("_id").in(ids)), new Update().set("status", RELEASED)
                    .set("releasedAt", now).set("updatedAt", now), OrderDetailBatchEntity.class);
        }
    }

    public List<PlannedBatch> preview(List<Demand> demands, Instant now) {
        return plan(demands, Map.of(), List.of(), BatchAvailability.shopDate(now)).stream()
                .map(pick -> new PlannedBatch(pick.key().lineId(), pick.batchId(), pick.quantity())).toList();
    }

    void issue(List<OrderDetailEntity> lines, List<PlannedBatch> required, Instant now) {
        List<OrderDetailBatchEntity> reservations = allocations(lines, RESERVED);
        requireFullHold(lines, reservations);
        Map<String, BigDecimal> ownHolds = reservations.stream().collect(Collectors.toMap(
                OrderDetailBatchEntity::getBatchId, OrderDetailBatchEntity::getQuantity, BigDecimal::add));
        LocalDate today = BatchAvailability.shopDate(now);
        List<Pick> pinned = pinned(required);
        Map<LineBatch, BigDecimal> reserved = reservations.stream().collect(Collectors.toMap(
                row -> new LineBatch(row.getOrderDetailId(), row.getBatchId()), OrderDetailBatchEntity::getQuantity));
        for (Pick pick : pinned) {
            if (reserved.getOrDefault(pick.key(), BigDecimal.ZERO).compareTo(pick.quantity()) < 0) {
                throw new ApiException(ApiError.STOCK_ALLOCATION_INVALID);
            }
        }
        List<Pick> picks = plan(demands(lines), ownHolds, pinned, today);
        Map<String, StockChange> changes = new HashMap<>();
        ownHolds.forEach((id, quantity) -> accumulate(changes, id, quantity.negate(), BigDecimal.ZERO));
        picks.forEach(pick -> accumulate(changes, pick.batchId(), BigDecimal.ZERO, pick.quantity()));
        applyStockChanges(changes, now, today);
        recordIssue(reservations, picks, now);
    }

    List<Allocation> view(List<OrderDetailEntity> lines) {
        Map<String, OrderDetailEntity> details = lines.stream()
                .collect(Collectors.toMap(OrderDetailEntity::getId, Function.identity()));
        List<OrderDetailBatchEntity> allocations = allocations(lines, null);
        Set<String> ids = allocations.stream().map(OrderDetailBatchEntity::getBatchId).collect(Collectors.toSet());
        Map<String, ProductBatchEntity> batches = ids.isEmpty() ? Map.of() : mongo.find(
                Query.query(Criteria.where("_id").in(ids)), ProductBatchEntity.class).stream()
                .collect(Collectors.toMap(ProductBatchEntity::getId, Function.identity()));
        return allocations.stream().map(row -> {
            ProductBatchEntity batch = batches.get(row.getBatchId());
            OrderDetailEntity line = details.get(row.getOrderDetailId());
            BigDecimal released = row.getReleasedQuantity() == null && RELEASED.equals(row.getStatus())
                    ? row.getQuantity() : row.getReleasedQuantity();
            return new Allocation(row.getOrderDetailId(), line.getSku(), row.getBatchId(),
                    batch == null ? null : batch.getBatchCode(), batch == null ? null : batch.getExpiryDate(),
                    row.getQuantity(), released, row.getStatus(), row.getReservedAt(), row.getIssuedAt(),
                    row.getReleasedAt());
        }).toList();
    }

    private List<Pick> plan(List<Demand> lines, Map<String, BigDecimal> ownHolds, List<Pick> pinned, LocalDate today) {
        List<String> variants = lines.stream().map(Demand::variantId).distinct().toList();
        Criteria eligible = BatchAvailability.sellableBatches(variants, today);
        List<ProductBatchEntity> batches = mongo.find(Query.query(eligible).with(
                org.springframework.data.domain.Sort.by("expiryDate", "_id")), ProductBatchEntity.class);
        Map<String, List<ProductBatchEntity>> byVariant = batches.stream()
                .collect(Collectors.groupingBy(ProductBatchEntity::getVariantId));
        Map<String, BigDecimal> available = new HashMap<>();
        for (ProductBatchEntity batch : batches) {
            BigDecimal otherHolds = BatchAvailability.amount(batch.getQuantityReserved())
                    .subtract(ownHolds.getOrDefault(batch.getId(), BigDecimal.ZERO));
            if (otherHolds.signum() < 0) {
                throw new ApiException(ApiError.STOCK_ALLOCATION_INVALID);
            }
            available.put(batch.getId(), BatchAvailability.amount(batch.getQuantityOnHand())
                    .subtract(otherHolds).max(BigDecimal.ZERO));
        }

        Map<String, BigDecimal> pinnedByLine = new HashMap<>();
        Map<LineBatch, BigDecimal> picks = new LinkedHashMap<>();
        for (Pick pick : pinned) {
            BigDecimal free = available.getOrDefault(pick.batchId(), BigDecimal.ZERO);
            if (free.compareTo(pick.quantity()) < 0) {
                throw new ApiException(ApiError.PROMOTION_BATCH_NO_LONGER_AVAILABLE);
            }
            available.put(pick.batchId(), free.subtract(pick.quantity()));
            pinnedByLine.merge(pick.key().lineId(), pick.quantity(), BigDecimal::add);
            picks.merge(pick.key(), pick.quantity(), BigDecimal::add);
        }
        for (Demand line : lines) {
            BigDecimal remaining = line.quantity().subtract(pinnedByLine.getOrDefault(line.lineId(), BigDecimal.ZERO));
            if (remaining.signum() < 0) {
                throw new ApiException(ApiError.STOCK_ALLOCATION_INVALID);
            }
            for (ProductBatchEntity batch : byVariant.getOrDefault(line.variantId(), List.of())) {
                BigDecimal free = available.get(batch.getId());
                BigDecimal quantity = free.min(remaining);
                if (quantity.signum() > 0) {
                    picks.merge(new LineBatch(line.lineId(), batch.getId()), quantity, BigDecimal::add);
                    available.put(batch.getId(), free.subtract(quantity));
                    remaining = remaining.subtract(quantity);
                }
                if (remaining.signum() == 0) {
                    break;
                }
            }
            if (remaining.signum() > 0) {
                throw new ApiException(ApiError.INSUFFICIENT_STOCK);
            }
        }
        return picks.entrySet().stream().map(entry -> new Pick(entry.getKey(), entry.getValue())).toList();
    }

    private static List<Demand> demands(List<OrderDetailEntity> lines) {
        return lines.stream().map(line -> new Demand(line.getId(), line.getVariantId(), line.getQuantity())).toList();
    }

    private static List<Pick> pinned(List<PlannedBatch> required) {
        return required.stream().map(row -> new Pick(new LineBatch(row.lineId(), row.batchId()), row.quantity()))
                .toList();
    }

    private static void requireFullHold(List<OrderDetailEntity> lines, List<OrderDetailBatchEntity> rows) {
        if (lines.isEmpty()) {
            throw new ApiException(ApiError.STOCK_ALLOCATION_INVALID);
        }
        Map<String, BigDecimal> quantities = rows.stream().collect(Collectors.toMap(
                OrderDetailBatchEntity::getOrderDetailId, OrderDetailBatchEntity::getQuantity, BigDecimal::add));
        for (OrderDetailEntity line : lines) {
            if (line.getQuantity() == null || line.getQuantity().signum() <= 0
                    || quantities.getOrDefault(line.getId(), BigDecimal.ZERO).compareTo(line.getQuantity()) != 0) {
                throw new ApiException(ApiError.STOCK_ALLOCATION_INVALID);
            }
        }
    }

    private void applyStockChanges(Map<String, StockChange> changes, Instant now, LocalDate today) {
        if (changes.isEmpty()) {
            return;
        }
        var updates = mongo.bulkOps(BulkMode.ORDERED, ProductBatchEntity.class);
        changes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            StockChange change = entry.getValue();
            Decimal128 reserveDelta = new Decimal128(change.reserveDelta());
            Decimal128 issued = new Decimal128(change.issued());
            Criteria batch = Criteria.where("_id").is(entry.getKey());
            if (change.issued().signum() > 0 || change.reserveDelta().signum() > 0) {
                batch.and("status").is("ACTIVE").and("expiryDate").gte(today);
            }
            Document reserved = new Document("$ifNull", List.of("$quantityReserved", Decimal128.POSITIVE_ZERO));
            Document remaining = new Document("$add", List.of(reserved, reserveDelta));
            Document guard = new Document("$gte", List.of(remaining, Decimal128.POSITIVE_ZERO));
            if (change.issued().signum() > 0 || change.reserveDelta().signum() > 0) {
                Document onHand = new Document("$ifNull", List.of("$quantityOnHand", Decimal128.POSITIVE_ZERO));
                Document afterIssue = new Document("$subtract", List.of(onHand, issued));
                guard = new Document("$and", List.of(guard, new Document("$gte", List.of(afterIssue, remaining))));
            }
            Document expression = guard;
            Query filter = Query.query(batch).addCriteria(Criteria.expr(() -> expression));
            Update update = new Update().inc("quantityReserved", reserveDelta).inc("version", 1).set("updatedAt", now);
            if (change.issued().signum() > 0) {
                update.inc("quantityOnHand", new Decimal128(change.issued().negate()));
            }
            updates.updateOne(filter, update);
        });
        if (updates.execute().getMatchedCount() != changes.size()) {
            throw new ApiException(ApiError.STOCK_ALLOCATION_INVALID);
        }
    }

    private void recordIssue(List<OrderDetailBatchEntity> reservations, List<Pick> picks, Instant now) {
        Map<LineBatch, Pick> remaining = picks.stream()
                .collect(Collectors.toMap(Pick::key, Function.identity()));
        var updates = mongo.bulkOps(BulkMode.ORDERED, OrderDetailBatchEntity.class);
        for (OrderDetailBatchEntity row : reservations) {
            Pick picked = remaining.remove(new LineBatch(row.getOrderDetailId(), row.getBatchId()));
            Update update = new Update().set("updatedAt", now);
            if (picked == null) {
                update.set("status", RELEASED).set("releasedAt", now)
                        .set("releasedQuantity", new Decimal128(row.getQuantity()));
            } else {
                update.set("status", ISSUED).set("issuedAt", now).set("quantity", new Decimal128(picked.quantity()));
                BigDecimal unused = row.getQuantity().subtract(picked.quantity());
                if (unused.signum() > 0) {
                    update.set("releasedAt", now).set("releasedQuantity", new Decimal128(unused));
                }
            }
            updates.updateOne(Query.query(Criteria.where("_id").is(row.getId()).and("status").is(RESERVED)), update);
        }
        if (updates.execute().getMatchedCount() != reservations.size()) {
            throw new ApiException(ApiError.STOCK_ALLOCATION_INVALID);
        }
        if (!remaining.isEmpty()) {
            List<OrderDetailBatchEntity> newRows = remaining.values().stream()
                    .map(pick -> allocation(pick, ISSUED, now)).toList();
            mongo.insert(newRows, OrderDetailBatchEntity.class);
        }
    }

    private List<OrderDetailBatchEntity> allocations(List<OrderDetailEntity> lines, String status) {
        List<String> ids = lines.stream().map(OrderDetailEntity::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Criteria filter = Criteria.where("orderDetailId").in(ids);
        if (status != null) {
            filter.and("status").is(status);
        }
        return mongo.find(Query.query(filter).with(org.springframework.data.domain.Sort.by("reservedAt", "_id")),
                OrderDetailBatchEntity.class);
    }

    private static OrderDetailBatchEntity allocation(Pick pick, String status, Instant now) {
        OrderDetailBatchEntity row = new OrderDetailBatchEntity();
        row.setId(UUID.randomUUID().toString());
        row.setOrderDetailId(pick.key().lineId());
        row.setBatchId(pick.batchId());
        row.setQuantity(pick.quantity());
        row.setStatus(status);
        row.setReservedAt(now);
        if (ISSUED.equals(status)) {
            row.setIssuedAt(now);
        }
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    private static void accumulate(Map<String, StockChange> changes, String batchId, BigDecimal delta, BigDecimal issued) {
        changes.merge(batchId, new StockChange(delta, issued), (left, right) ->
                new StockChange(left.reserveDelta().add(right.reserveDelta()), left.issued().add(right.issued())));
    }

    private record StockChange(BigDecimal reserveDelta, BigDecimal issued) {}
    private record LineBatch(String lineId, String batchId) {}
    private record Pick(LineBatch key, BigDecimal quantity) {
        String batchId() {
            return key.batchId();
        }
    }

    public record Allocation(String orderDetailId, String sku, String batchId, String batchCode, LocalDate expiryDate,
                             BigDecimal quantity, BigDecimal releasedQuantity, String status, Instant reservedAt,
                             Instant issuedAt, Instant releasedAt) {}

    public record Demand(String lineId, String variantId, BigDecimal quantity) {}
    public record PlannedBatch(String lineId, String batchId, BigDecimal quantity) {}
}
