package vn.ducthe.organic.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bson.types.Decimal128;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.inventory.BatchAvailability;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.ReceivingPayloads;
import vn.ducthe.organic.dto.request.ReceivingPayloads.ReceiptInput;
import vn.ducthe.organic.dto.request.ReceivingPayloads.ReceiptStatus;
import vn.ducthe.organic.dto.response.GoodsReceiptResponses.Detail;
import vn.ducthe.organic.dto.response.GoodsReceiptResponses.Line;
import vn.ducthe.organic.dto.response.GoodsReceiptResponses.ReceivingVariant;
import vn.ducthe.organic.dto.response.GoodsReceiptResponses.Summary;
import vn.ducthe.organic.entity.BaseEntity;
import vn.ducthe.organic.entity.EmployeeEntity;
import vn.ducthe.organic.entity.GoodsReceiptDetailEntity;
import vn.ducthe.organic.entity.GoodsReceiptEntity;
import vn.ducthe.organic.entity.ProductBatchEntity;
import vn.ducthe.organic.entity.ProductEntity;
import vn.ducthe.organic.entity.ProductVariantEntity;
import vn.ducthe.organic.entity.SupplierEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.repository.OrderTransactions;
import vn.ducthe.organic.service.GoodsReceiptService;
import vn.ducthe.organic.service.SupplierService;

@Service
public class GoodsReceiptServiceImpl implements GoodsReceiptService {
    private final MongoDocumentRepository mongo;
    private final SupplierService suppliers;
    private final OrderTransactions transactions;

    public GoodsReceiptServiceImpl(MongoDocumentRepository mongo, SupplierService suppliers, OrderTransactions transactions) {
        this.mongo = mongo;
        this.suppliers = suppliers;
        this.transactions = transactions;
    }

    @Override
    public PageData<Summary> list(String supplierId, ReceiptStatus status, int page, int size) {
        Query filter = new Query();
        if (supplierId != null && !supplierId.isBlank()) {
            filter.addCriteria(Criteria.where("supplierId").is(supplierId));
        }
        if (status != null) {
            filter.addCriteria(Criteria.where("status").is(status.name()));
        }
        var pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "receivedAt", "_id"));
        long total = mongo.count(filter, GoodsReceiptEntity.class);
        List<GoodsReceiptEntity> rows = mongo.find(Query.of(filter).with(pageable), GoodsReceiptEntity.class);
        return PageData.from(new PageImpl<>(summaries(rows), pageable, total));
    }

    @Override
    public Detail detail(String receiptId) {
        GoodsReceiptEntity receipt = receipt(receiptId);
        List<GoodsReceiptDetailEntity> details = lines(receiptId);
        Set<String> variantIds = new HashSet<>();
        Set<String> batchIds = new HashSet<>();
        for (GoodsReceiptDetailEntity row : details) {
            if (row.getSku() == null) {
                add(variantIds, row.getVariantId());
            }
            add(batchIds, row.getBatchId());
        }
        Map<String, ProductVariantEntity> variants = related(variantIds, ProductVariantEntity.class);
        Map<String, ProductBatchEntity> batches = related(batchIds, ProductBatchEntity.class);
        List<Line> items = details.stream().map(row ->
                line(row, variants.get(row.getVariantId()), row.getBatchId() == null ? null : batches.get(row.getBatchId())))
                .toList();
        return new Detail(summaries(List.of(receipt)).getFirst(), items);
    }

    @Override
    public PageData<ReceivingVariant> variants(String q, int page, int size) {
        Query filter = new Query();
        if (q != null && !q.isBlank()) {
            Pattern search = Pattern.compile(Pattern.quote(q.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            filter.addCriteria(new Criteria().orOperator(Criteria.where("sku").regex(search),
                    Criteria.where("name").regex(search), Criteria.where("barcode").regex(search)));
        }
        var pageable = PageRequest.of(page - 1, size, Sort.by("sku", "_id"));
        long total = mongo.count(filter, ProductVariantEntity.class);
        List<ProductVariantEntity> rows = mongo.find(Query.of(filter).with(pageable), ProductVariantEntity.class);
        var productIds = rows.stream().map(ProductVariantEntity::getProductId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        var names = names(related(productIds, ProductEntity.class), ProductEntity::getName);
        var items = rows.stream().map(row -> new ReceivingVariant(row.getId(), row.getSku(), row.getName(),
                names.get(row.getProductId()), row.getUnitCode(), row.getStatus())).toList();
        return PageData.from(new PageImpl<>(items, pageable, total));
    }

    @Override
    public Detail create(String accountId, ReceiptInput input) {
        String receiptId = transactions.execute(() -> {
            EmployeeEntity receiver = employee(accountId);
            suppliers.active(input.supplierId());
            Instant now = Instant.now();
            GoodsReceiptEntity receipt = new GoodsReceiptEntity();
            stamp(receipt, now);
            receipt.setReceiptCode("PN-" + receipt.getId());
            receipt.setReceivedByEmployeeId(receiver.getId());
            receipt.setReceivedAt(now);
            receipt.setStatus(ReceiptStatus.DRAFT.name());
            receipt.setSupplierId(input.supplierId());
            receipt.setNote(SupplierServiceImpl.clean(input.note()));
            List<GoodsReceiptDetailEntity> lines = draftLines(receipt.getId(), input, now);
            receipt.setTotalCost(total(lines));
            mongo.insert(receipt);
            mongo.insert(lines, GoodsReceiptDetailEntity.class);
            return receipt.getId();
        });
        return detail(receiptId);
    }

    @Override
    public Detail edit(String accountId, String receiptId, ReceiptInput input) {
        transactions.execute(() -> {
            employee(accountId);
            requireDraft(receipt(receiptId));
            suppliers.active(input.supplierId());
            Instant now = Instant.now();
            List<GoodsReceiptDetailEntity> lines = draftLines(receiptId, input, now);
            updateDraft(receiptId, new Update().set("supplierId", input.supplierId())
                    .set("note", SupplierServiceImpl.clean(input.note()))
                    .set("totalCost", new Decimal128(total(lines))), now);
            mongo.remove(by("goodsReceiptId", receiptId), GoodsReceiptDetailEntity.class);
            mongo.insert(lines, GoodsReceiptDetailEntity.class);
            return null;
        });
        return detail(receiptId);
    }

    @Override
    public Detail confirm(String accountId, String receiptId, String note) {
        transactions.execute(() -> {
            EmployeeEntity confirmer = employee(accountId);
            GoodsReceiptEntity receipt = receipt(receiptId);
            if (ReceiptStatus.CONFIRMED.name().equals(receipt.getStatus())) {
                return null;
            }
            requireDraft(receipt);
            suppliers.active(receipt.getSupplierId());
            List<GoodsReceiptDetailEntity> lines = lines(receiptId);
            if (lines.isEmpty()) {
                throw new ApiException(ApiError.VALIDATION_ERROR);
            }
            Instant now = Instant.now();
            updateDraft(receiptId, new Update().set("status", ReceiptStatus.CONFIRMED.name())
                    .set("confirmedByEmployeeId", confirmer.getId()).set("confirmedAt", now)
                    .set("confirmationNote", SupplierServiceImpl.clean(note)), now);

            List<ProductBatchEntity> batches = new ArrayList<>();
            for (GoodsReceiptDetailEntity line : lines) {
                if (accepted(line).signum() > 0) {
                    ProductBatchEntity batch = batch(receipt, line, now);
                    line.setBatchId(batch.getId());
                    line.setUpdatedAt(now);
                    batches.add(batch);
                }
            }
            if (!batches.isEmpty()) {
                mongo.insert(batches, ProductBatchEntity.class);
                var updates = mongo.bulkOps(org.springframework.data.mongodb.core.BulkOperations.BulkMode.ORDERED,
                        GoodsReceiptDetailEntity.class);
                lines.stream().filter(line -> line.getBatchId() != null)
                        .forEach(line -> updates.updateOne(by("_id", line.getId()),
                                new Update().set("batchId", line.getBatchId()).set("updatedAt", now)));
                updates.execute();
            }
            return null;
        });
        return detail(receiptId);
    }

    @Override
    public Detail cancel(String accountId, String receiptId, String reason) {
        transactions.execute(() -> {
            EmployeeEntity employee = employee(accountId);
            GoodsReceiptEntity receipt = receipt(receiptId);
            if (ReceiptStatus.CANCELLED.name().equals(receipt.getStatus())) {
                return null;
            }
            requireDraft(receipt);
            Instant now = Instant.now();
            updateDraft(receiptId, new Update().set("status", ReceiptStatus.CANCELLED.name())
                    .set("cancelledByEmployeeId", employee.getId()).set("cancelledAt", now)
                    .set("cancellationReason", reason.strip()), now);
            return null;
        });
        return detail(receiptId);
    }

    private List<GoodsReceiptDetailEntity> draftLines(String receiptId, ReceiptInput input, Instant now) {
        Set<String> variantIds = input.items().stream().map(ReceivingPayloads.LineInput::variantId)
                .collect(Collectors.toSet());
        var variants = related(variantIds, ProductVariantEntity.class);
        if (variants.size() != variantIds.size()) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        List<GoodsReceiptDetailEntity> rows = new ArrayList<>();
        LocalDate today = BatchAvailability.shopDate(now);
        for (var item : input.items()) {
            if (item.quantityRejected().compareTo(item.quantityReceived()) > 0) {
                throw new ApiException(ApiError.RECEIPT_QUANTITY_INVALID);
            }
            String reason = SupplierServiceImpl.clean(item.discrepancyReason());
            if (item.quantityRejected().signum() > 0 && reason == null) {
                throw new ApiException(ApiError.RECEIPT_REJECTION_REASON_REQUIRED);
            }
            GoodsReceiptDetailEntity row = new GoodsReceiptDetailEntity();
            stamp(row, now);
            row.setGoodsReceiptId(receiptId);
            row.setLineNumber(rows.size() + 1);
            row.setVariantId(item.variantId());
            ProductVariantEntity variant = variants.get(item.variantId());
            row.setSku(variant.getSku());
            row.setVariantName(variant.getName());
            row.setQuantityReceived(item.quantityReceived());
            row.setQuantityRejected(item.quantityRejected());
            row.setUnitCost(item.unitCost());
            row.setDiscrepancyReason(reason);
            row.setLotCode(SupplierServiceImpl.clean(item.lotCode()));
            row.setManufactureDate(item.manufactureDate());
            row.setExpiryDate(item.expiryDate());
            row.setOriginDescription(SupplierServiceImpl.clean(item.originDescription()));
            row.setLineTotal(accepted(row).multiply(item.unitCost()));
            if (accepted(row).signum() > 0) {
                validateBatch(row, today);
            }
            rows.add(row);
        }
        return rows;
    }

    private static ProductBatchEntity batch(GoodsReceiptEntity receipt, GoodsReceiptDetailEntity line, Instant now) {
        validateBatch(line, BatchAvailability.shopDate(now));
        ProductBatchEntity batch = new ProductBatchEntity();
        stamp(batch, now);
        batch.setVariantId(line.getVariantId());
        batch.setBatchCode(receipt.getReceiptCode() + "-" + line.getLineNumber());
        batch.setLotCode(line.getLotCode());
        batch.setManufactureDate(line.getManufactureDate());
        batch.setExpiryDate(line.getExpiryDate());
        batch.setOriginDescription(line.getOriginDescription());
        batch.setQuantityOnHand(accepted(line));
        batch.setQuantityReserved(BigDecimal.ZERO);
        batch.setStatus(AuthStore.ACTIVE);
        return batch;
    }

    private static void validateBatch(GoodsReceiptDetailEntity line, LocalDate today) {
        LocalDate manufacture = line.getManufactureDate();
        LocalDate expiry = line.getExpiryDate();
        if (line.getLotCode() == null || line.getOriginDescription() == null || manufacture == null || expiry == null
                || manufacture.isAfter(today) || !expiry.isAfter(manufacture) || expiry.isBefore(today)) {
            throw new ApiException(ApiError.RECEIPT_BATCH_INVALID);
        }
    }

    private void updateDraft(String id, Update update, Instant now) {
        Query filter = Query.query(Criteria.where("_id").is(id).and("status").is(ReceiptStatus.DRAFT.name()));
        if (mongo.updateFirst(filter, update.set("updatedAt", now), GoodsReceiptEntity.class).getMatchedCount() == 0) {
            throw new ApiException(ApiError.RECEIPT_NOT_DRAFT);
        }
    }

    private List<Summary> summaries(List<GoodsReceiptEntity> rows) {
        Set<String> supplierIds = new HashSet<>();
        Set<String> employeeIds = new HashSet<>();
        for (GoodsReceiptEntity row : rows) {
            add(supplierIds, row.getSupplierId());
            add(employeeIds, row.getReceivedByEmployeeId());
            add(employeeIds, row.getConfirmedByEmployeeId());
            add(employeeIds, row.getCancelledByEmployeeId());
        }
        var supplierNames = names(related(supplierIds, SupplierEntity.class), SupplierEntity::getName);
        var employeeNames = names(related(employeeIds, EmployeeEntity.class), EmployeeEntity::getFullName);
        return rows.stream().map(row -> new Summary(row, supplierNames.get(row.getSupplierId()),
                employeeNames.get(row.getReceivedByEmployeeId()), employeeNames.get(row.getConfirmedByEmployeeId()),
                employeeNames.get(row.getCancelledByEmployeeId()))).toList();
    }

    private static <T> Map<String, String> names(Map<String, T> rows, Function<T, String> name) {
        Map<String, String> result = new java.util.HashMap<>();
        rows.forEach((id, row) -> result.put(id, name.apply(row)));
        return result;
    }

    private <T extends BaseEntity> Map<String, T> related(Set<String> ids, Class<T> type) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mongo.find(Query.query(Criteria.where("_id").in(ids)), type).stream()
                .collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
    }

    private GoodsReceiptEntity receipt(String id) {
        GoodsReceiptEntity row = mongo.findById(id, GoodsReceiptEntity.class);
        if (row == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private List<GoodsReceiptDetailEntity> lines(String id) {
        return mongo.find(by("goodsReceiptId", id).with(Sort.by("lineNumber", "_id")), GoodsReceiptDetailEntity.class);
    }

    private EmployeeEntity employee(String accountId) {
        EmployeeEntity row = mongo.findOne(Query.query(Criteria.where("accountId").is(accountId)
                .and("workStatus").is(AuthStore.ACTIVE)), EmployeeEntity.class);
        if (row == null) {
            throw new ApiException(ApiError.EMPLOYEE_REQUIRED);
        }
        return row;
    }

    private static void requireDraft(GoodsReceiptEntity receipt) {
        if (!ReceiptStatus.DRAFT.name().equals(receipt.getStatus())) {
            throw new ApiException(ApiError.RECEIPT_NOT_DRAFT);
        }
    }

    private static BigDecimal accepted(GoodsReceiptDetailEntity line) {
        return line.getQuantityReceived().subtract(line.getQuantityRejected() == null
                ? BigDecimal.ZERO : line.getQuantityRejected());
    }

    private static BigDecimal total(List<GoodsReceiptDetailEntity> lines) {
        return lines.stream().map(GoodsReceiptDetailEntity::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void stamp(BaseEntity row, Instant now) {
        row.setId(UUID.randomUUID().toString());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
    }

    private static void add(Set<String> ids, String id) {
        if (id != null) {
            ids.add(id);
        }
    }

    private static Query by(String field, Object value) {
        return Query.query(Criteria.where(field).is(value));
    }

    private static Line line(GoodsReceiptDetailEntity row, ProductVariantEntity variant, ProductBatchEntity batch) {
        String sku = variant == null ? row.getSku() : variant.getSku();
        String name = variant == null ? row.getVariantName() : variant.getName();
        return new Line(row.getId(), row.getLineNumber(), row.getVariantId(), sku, name,
                row.getBatchId(), batch == null ? null : batch.getBatchCode(),
                row.getLotCode() != null || batch == null ? row.getLotCode() : batch.getLotCode(),
                row.getManufactureDate() != null || batch == null ? row.getManufactureDate() : batch.getManufactureDate(),
                row.getExpiryDate() != null || batch == null ? row.getExpiryDate() : batch.getExpiryDate(),
                row.getOriginDescription() != null || batch == null ? row.getOriginDescription() : batch.getOriginDescription(),
                row.getQuantityReceived(), row.getQuantityRejected(), accepted(row), row.getUnitCost(), row.getLineTotal(),
                row.getDiscrepancyReason());
    }
}
