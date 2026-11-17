package vn.ducthe.organic.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import vn.ducthe.organic.service.InventoryService.Expiry;

public final class InventoryResponses {
    private InventoryResponses() {}

    public record Batch(String id, String variantId, String sku, String variantName, String batchCode, String lotCode,
                 LocalDate manufactureDate, LocalDate expiryDate, String originDescription, String status,
                 Expiry expiry, Long daysUntilExpiry, BigDecimal quantityOnHand, BigDecimal quantityReserved,
                 BigDecimal quantityAvailable, String statusReason, Instant statusChangedAt,
                 String statusChangedByAccountId) {}

    public record VariantStock(String variantId, String productId, String sku, String name, String status,
                        BigDecimal quantityOnHand, BigDecimal quantityReserved, BigDecimal quantityAvailable,
                        BigDecimal reorderLevel, boolean lowStock) {}
}
