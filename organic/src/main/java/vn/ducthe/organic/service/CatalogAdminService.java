package vn.ducthe.organic.service;

import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.CategoryInput;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.ProductInput;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.Status;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.VariantInput;
import vn.ducthe.organic.entity.CategoryEntity;
import vn.ducthe.organic.entity.ProductEntity;
import vn.ducthe.organic.entity.ProductVariantEntity;

public interface CatalogAdminService {

    PageData<CategoryEntity> categories(String text, Status status, int page, int size);

    CategoryEntity category(String id);

    CategoryEntity createCategory(CategoryInput input);

    CategoryEntity updateCategory(String id, CategoryInput input);

    CategoryEntity categoryStatus(String id, Status status);

    PageData<ProductEntity> products(String text, Status status, String categoryId, int page, int size);

    ProductEntity product(String id);

    ProductEntity createProduct(ProductInput input);

    ProductEntity updateProduct(String id, ProductInput input);

    ProductEntity productStatus(String id, Status status);

    PageData<ProductVariantEntity> variants(String productId, Status status, int page, int size);

    ProductVariantEntity variantDetail(String productId, String variantId);

    ProductVariantEntity createVariant(String productId, VariantInput input);

    ProductVariantEntity updateVariant(String productId, String variantId, VariantInput input);

    ProductVariantEntity variantStatus(String productId, String variantId, Status status);
}
