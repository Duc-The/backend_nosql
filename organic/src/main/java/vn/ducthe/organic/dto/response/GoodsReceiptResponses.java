package vn.ducthe.organic.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import vn.ducthe.organic.entity.GoodsReceiptEntity;

public final class GoodsReceiptResponses {
    private GoodsReceiptResponses() {}

    public record Summary(GoodsReceiptEntity receipt, String supplierName, String receivedByName, String confirmedByName,
                   String cancelledByName) {}

    public record Detail(Summary summary, List<Line> items) {}

    public record ReceivingVariant(String id, String sku, String name, String productName, String unitCode, String status) {}

    public record Line(String id, Integer lineNumber, String variantId, String sku, String variantName, String batchId,
                String batchCode, String lotCode, LocalDate manufactureDate, LocalDate expiryDate, String originDescription,
                BigDecimal quantityReceived, BigDecimal quantityRejected, BigDecimal quantityAccepted, BigDecimal unitCost,
                BigDecimal lineTotal, String discrepancyReason) {}
}
