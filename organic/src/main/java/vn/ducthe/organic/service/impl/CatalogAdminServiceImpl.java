package vn.ducthe.organic.service.impl;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.CategoryInput;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.ProductInput;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.Status;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.VariantInput;
import vn.ducthe.organic.entity.BaseEntity;
import vn.ducthe.organic.entity.CategoryEntity;
import vn.ducthe.organic.entity.ProductEntity;
import vn.ducthe.organic.entity.ProductVariantEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.repository.OrderTransactions;
import vn.ducthe.organic.service.CatalogAdminService;

@Service
public class CatalogAdminServiceImpl implements CatalogAdminService {
    private static final String ADMIN_STATE = "catalog_administration";
    private static final String HIERARCHY = "hierarchy";
    private final MongoDocumentRepository mongo;
    private final OrderTransactions transactions;

    public CatalogAdminServiceImpl(MongoDocumentRepository mongo, OrderTransactions transactions) {
        this.mongo = mongo;
        this.transactions = transactions;
    }

    @Override
    public PageData<CategoryEntity> categories(String text, Status status, int page, int size) {
        return list(CategoryEntity.class, filters(text, status, "categoryCode"), Sort.by("sortOrder", "name", "_id"),
                page, size);
    }

    @Override
    public CategoryEntity category(String id) {
        return required(mongo.findById(id, CategoryEntity.class));
    }

    @Override
    public CategoryEntity createCategory(CategoryInput input) {
        String parent = clean(input.parentCategoryId());
        if (parent != null) {
            category(parent);
        }
        CategoryEntity row = new CategoryEntity();
        stamp(row);
        row.setCategoryCode(code(input.categoryCode()));
        row.setName(input.name().strip());
        row.setSlug(input.slug());
        row.setParentCategoryId(parent);
        row.setDescription(clean(input.description()));
        row.setImageUrl(input.imageUrl());
        row.setSortOrder(input.sortOrder());
        row.setStatus(input.status().name());
        return mongo.insert(row);
    }

    @Override
    public CategoryEntity updateCategory(String id, CategoryInput input) {
        initializeHierarchyGuard();
        return transactions.execute(() -> {
            mongo.updateFirst(id(HIERARCHY), new Update().inc("revision", 1), Document.class, ADMIN_STATE);
            category(id);
            String parent = clean(input.parentCategoryId());
            checkParent(id, parent);
            return update(id(id), new Update().set("categoryCode", code(input.categoryCode()))
                    .set("name", input.name().strip()).set("slug", input.slug()).set("parentCategoryId", parent)
                    .set("description", clean(input.description())).set("imageUrl", input.imageUrl())
                    .set("sortOrder", input.sortOrder()).set("status", input.status().name()), CategoryEntity.class);
        });
    }

    private void initializeHierarchyGuard() {
        try {
            mongo.upsert(id(HIERARCHY), new Update().setOnInsert("revision", 0), Document.class, ADMIN_STATE);
        } catch (DuplicateKeyException exception) {
            if (!mongo.exists(id(HIERARCHY), ADMIN_STATE)) {
                throw exception;
            }
        }
    }

    private void checkParent(String categoryId, String parent) {
        var visited = new HashSet<String>();
        visited.add(categoryId);
        String current = parent;
        while (current != null) {
            if (!visited.add(current)) {
                throw new ApiException(ApiError.CATEGORY_CYCLE);
            }
            current = category(current).getParentCategoryId();
        }
    }

    @Override
    public CategoryEntity categoryStatus(String id, Status status) {
        return update(id(id), new Update().set("status", status.name()), CategoryEntity.class);
    }

    @Override
    public PageData<ProductEntity> products(String text, Status status, String categoryId, int page, int size) {
        Query filter = filters(text, status, "productCode");
        if (categoryId != null && !categoryId.isBlank()) {
            filter.addCriteria(Criteria.where("categoryId").is(categoryId));
        }
        return list(ProductEntity.class, filter, Sort.by("name", "_id"), page, size);
    }

    @Override
    public ProductEntity product(String id) {
        return required(mongo.findById(id, ProductEntity.class));
    }

    @Override
    public ProductEntity createProduct(ProductInput input) {
        category(input.categoryId());
        ProductEntity row = new ProductEntity();
        stamp(row);
        row.setProductCode(code(input.productCode()));
        row.setName(input.name().strip());
        row.setSlug(input.slug());
        row.setCategoryId(input.categoryId());
        row.setBrandName(clean(input.brandName()));
        row.setOriginDescription(clean(input.originDescription()));
        row.setDescription(clean(input.description()));
        row.setStorageInstructions(clean(input.storageInstructions()));
        row.setFeatured(input.featured());
        row.setFeaturedOrder(input.featuredOrder());
        row.setStatus(input.status().name());
        return mongo.insert(row);
    }

    @Override
    public ProductEntity updateProduct(String id, ProductInput input) {
        category(input.categoryId());
        return update(id(id), new Update().set("productCode", code(input.productCode()))
                .set("name", input.name().strip()).set("slug", input.slug()).set("categoryId", input.categoryId())
                .set("brandName", clean(input.brandName())).set("originDescription", clean(input.originDescription()))
                .set("description", clean(input.description())).set("storageInstructions", clean(input.storageInstructions()))
                .set("featured", input.featured()).set("featuredOrder", input.featuredOrder())
                .set("status", input.status().name()), ProductEntity.class);
    }

    @Override
    public ProductEntity productStatus(String id, Status status) {
        return update(id(id), new Update().set("status", status.name()), ProductEntity.class);
    }

    @Override
    public PageData<ProductVariantEntity> variants(String productId, Status status, int page, int size) {
        product(productId);
        Query filter = filters(null, status, "sku").addCriteria(Criteria.where("productId").is(productId));
        return list(ProductVariantEntity.class, filter, Sort.by("netQuantity", "sku", "_id"),
                page, size);
    }

    @Override
    public ProductVariantEntity variantDetail(String productId, String variantId) {
        return required(mongo.findOne(variant(productId, variantId), ProductVariantEntity.class));
    }

    @Override
    public ProductVariantEntity createVariant(String productId, VariantInput input) {
        product(productId);
        validatePrice(input);
        ProductVariantEntity row = new ProductVariantEntity();
        stamp(row);
        row.setProductId(productId);
        row.setSku(code(input.sku()));
        row.setName(input.name().strip());
        row.setBarcode(clean(input.barcode()));
        row.setNetQuantity(input.netQuantity());
        row.setUnitCode(code(input.unitCode()));
        row.setSellingPrice(input.sellingPrice());
        row.setCompareAtPrice(input.compareAtPrice());
        row.setReorderLevel(input.reorderLevel());
        row.setStatus(input.status().name());
        return mongo.insert(row);
    }

    @Override
    public ProductVariantEntity updateVariant(String productId, String variantId, VariantInput input) {
        validatePrice(input);
        return update(variant(productId, variantId), new Update().set("sku", code(input.sku()))
                .set("name", input.name().strip()).set("barcode", clean(input.barcode()))
                .set("netQuantity", new Decimal128(input.netQuantity())).set("unitCode", code(input.unitCode()))
                .set("sellingPrice", new Decimal128(input.sellingPrice()))
                .set("compareAtPrice", input.compareAtPrice() == null ? null : new Decimal128(input.compareAtPrice()))
                .set("reorderLevel", new Decimal128(input.reorderLevel()))
                .set("status", input.status().name()), ProductVariantEntity.class);
    }

    @Override
    public ProductVariantEntity variantStatus(String productId, String variantId, Status status) {
        return update(variant(productId, variantId), new Update().set("status", status.name()), ProductVariantEntity.class);
    }

    private static void validatePrice(VariantInput input) {
        if (input.compareAtPrice() != null && input.compareAtPrice().compareTo(input.sellingPrice()) < 0) {
            throw new ApiException(ApiError.INVALID_COMPARE_AT_PRICE);
        }
    }

    private <T> T update(Query query, Update change, Class<T> type) {
        return required(mongo.findAndModify(query, change.set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), type));
    }

    private <T> PageData<T> list(Class<T> type, Query filter, Sort sort, int page, int size) {
        var pageable = PageRequest.of(page - 1, size, sort);
        long total = mongo.count(filter, type);
        List<T> items = mongo.find(Query.of(filter).with(pageable), type);
        return PageData.from(new PageImpl<>(items, pageable, total));
    }

    private static Query filters(String text, Status status, String codeField) {
        Query query = new Query();
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status.name()));
        }
        if (text != null && !text.isBlank()) {
            Pattern match = Pattern.compile(Pattern.quote(text.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            query.addCriteria(new Criteria().orOperator(Criteria.where("name").regex(match),
                    Criteria.where(codeField).regex(match)));
        }
        return query;
    }

    private static Query variant(String productId, String variantId) {
        return Query.query(Criteria.where("_id").is(variantId).and("productId").is(productId));
    }

    private static Query id(String value) {
        return Query.query(Criteria.where("_id").is(value));
    }

    private static String code(String value) {
        return value.toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static <T> T required(T value) {
        if (value == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return value;
    }

    private static void stamp(BaseEntity row) {
        Instant now = Instant.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
    }
}
