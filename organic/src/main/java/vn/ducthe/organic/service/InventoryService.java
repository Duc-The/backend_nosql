package vn.ducthe.organic.service;

import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.InventoryResponses.Batch;
import vn.ducthe.organic.dto.response.InventoryResponses.VariantStock;

public interface InventoryService {

    PageData<Batch> batches(String q, String variantId, BatchStatus status, Expiry expiry, boolean inStockOnly,
                            int page, int size);

    Batch detail(String id);

    Batch status(String id, String accountId, BatchStatus status, String reason);

    PageData<Batch> expiryReport(String variantId, boolean expired, int page, int size);

    PageData<VariantStock> skuStock(String q, boolean lowStockOnly, int page, int size);

    enum BatchStatus { ACTIVE, INACTIVE }

    enum Expiry { EXPIRED, EXPIRING_SOON, FRESH, UNKNOWN }
}
