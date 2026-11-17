package vn.ducthe.organic.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class BatchTraceabilityResponses {
    private BatchTraceabilityResponses() {}

    public record TracedBatch(String id, String batchCode, String lotCode, String variantId, String sku, String variantName,
                       LocalDate manufactureDate, LocalDate expiryDate, String originDescription, String status,
                       BigDecimal quantityOnHand, BigDecimal quantityReserved, List<Origin> origins) {}

    public record Origin(String receiptDetailId, String receiptId, String receiptCode, String receiptStatus, Instant receivedAt,
                  Instant confirmedAt, String supplierId, Supplier supplier, BigDecimal quantityReceived,
                  BigDecimal quantityRejected) {}

    public record Supplier(String id, String code, String name, String contactPerson, String phoneNumber, String email) {}

    public record IssuedOrder(String orderCode, String orderStatus, BigDecimal quantityIssued, Instant orderedAt,
                       Instant firstIssuedAt, Instant lastIssuedAt, Instant shippedAt, Instant deliveredAt) {}
}
