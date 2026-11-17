package vn.ducthe.organic.dto.response;

import java.math.BigDecimal;
import java.util.List;
import vn.ducthe.organic.entity.ProductEntity;

public final class CatalogResponses {
    private CatalogResponses() {}

    public record Category(String id, String name, String slug, String description, String imageUrl,
                           List<Category> children) {}

    public record Product(String id, String productCode, String name, String slug, String categoryId,
                          String brandName, String originDescription, List<ProductEntity.Image> images,
                          boolean featured, BigDecimal minPrice, BigDecimal maxPrice,
                          BigDecimal salePrice, BigDecimal saleCompareAtPrice) {}

    public record ProductDetail(String id, String productCode, String slug, String name, String categoryId,
                                String brandName, String originDescription, String description,
                                String storageInstructions, boolean featured, List<ProductImage> images,
                                List<Variant> variants) {}

    public record ProductImage(String url, String altText, int sortOrder, boolean primary) {}

    public record Variant(String id, String sku, String name, BigDecimal netQuantity, String unitCode,
                          BigDecimal sellingPrice, BigDecimal compareAtPrice) {}
}
