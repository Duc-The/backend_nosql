package vn.ducthe.organic.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.CatalogResponses.Category;
import vn.ducthe.organic.dto.response.CatalogResponses.Product;
import vn.ducthe.organic.dto.response.CatalogResponses.ProductDetail;
import vn.ducthe.organic.dto.response.CatalogResponses.ProductImage;
import vn.ducthe.organic.dto.response.CatalogResponses.Variant;
import vn.ducthe.organic.entity.CategoryEntity;
import vn.ducthe.organic.entity.OrderDetailEntity;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.ProductEntity;
import vn.ducthe.organic.entity.ProductVariantEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.service.CatalogService;

@Service
public class CatalogServiceImpl implements CatalogService {
    private static final String ACTIVE = "ACTIVE";
    private final MongoDocumentRepository mongo;

    public CatalogServiceImpl(MongoDocumentRepository mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<Category> categories() {
        Map<String, List<CategoryEntity>> children = groupChildren(activeCategories());
        return categoryTree(null, children);
    }

    @Override
    public PageData<Product> products(String q, String categorySlug, BigDecimal minPrice, BigDecimal maxPrice,
                                      boolean featured, Shelf shelf, int page, int size) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ApiException(ApiError.INVALID_PRICE_RANGE);
        }

        List<CategoryEntity> categories = activeCategories();
        Map<String, List<CategoryEntity>> children = groupChildren(categories);
        Set<String> visible = descendants(children.getOrDefault(null, List.of()), children);
        Set<String> categoryIds = visible;
        if (categorySlug != null && !categorySlug.isBlank()) {
            CategoryEntity selected = categories.stream()
                    .filter(category -> categorySlug.equals(category.getSlug()) && visible.contains(category.getId()))
                    .findFirst().orElseThrow(() -> new ApiException(ApiError.RESOURCE_NOT_FOUND));
            categoryIds = descendants(List.of(selected), children);
        }

        var pageable = PageRequest.of(page - 1, size);
        if (categoryIds.isEmpty()) {
            return PageData.from(new PageImpl<>(List.of(), pageable, 0));
        }

        Document match = new Document("status", ACTIVE).append("categoryId", new Document("$in", categoryIds));
        if (q != null && !q.isBlank()) {
            match.append("name", new Document("$regex", Pattern.quote(q.strip())).append("$options", "i"));
        }
        if (featured) {
            match.append("featured", true);
        }

        Document price = new Document("$type", "number").append("$gte",
                new Decimal128(minPrice == null ? BigDecimal.ZERO : minPrice));
        if (maxPrice != null) {
            price.append("$lte", new Decimal128(maxPrice));
        }
        Document discounted = new Document("$and", List.of(new Document("$isNumber", "$compareAtPrice"),
                new Document("$gt", List.of("$compareAtPrice", "$sellingPrice"))));
        List<Document> variantPipeline = new ArrayList<>();
        variantPipeline.add(new Document("$match", new Document("status", ACTIVE).append("sellingPrice", price)));
        variantPipeline.add(new Document("$set", new Document("discounted", discounted)));
        if (shelf == Shelf.DISCOUNTED) {
            variantPipeline.add(new Document("$match", new Document("discounted", true)));
        }
        variantPipeline.add(new Document("$group", new Document("_id", null)
                .append("minPrice", new Document("$min", "$sellingPrice"))
                .append("maxPrice", new Document("$max", "$sellingPrice"))
                .append("sale", new Document("$top", new Document("sortBy",
                        new Document("discounted", -1).append("sellingPrice", 1).append("_id", 1))
                        .append("output", new Document("discounted", "$discounted")
                                .append("price", "$sellingPrice").append("compareAtPrice", "$compareAtPrice"))))));
        Document lookup = new Document("from", mongo.getCollectionName(ProductVariantEntity.class))
                .append("localField", "productRef").append("foreignField", "productId")
                .append("pipeline", variantPipeline).append("as", "prices");
        Document sort = new Document("name", 1).append("_id", 1);
        if (shelf == Shelf.BESTSELLING) {
            sort = new Document("soldQuantity", -1).append("name", 1).append("_id", 1);
        } else if (featured) {
            sort = new Document("featuredOrder", 1).append("_id", 1);
        }
        Document fields = new Document("_id", 0).append("id", "$productRef")
                .append("productCode", 1).append("name", 1).append("slug", 1).append("categoryId", 1)
                .append("brandName", 1).append("originDescription", 1).append("images", 1).append("featured", 1)
                .append("minPrice", "$prices.minPrice").append("maxPrice", "$prices.maxPrice")
                .append("sale", "$prices.sale");
        Document facet = new Document("items", List.of(
                new Document("$sort", sort),
                new Document("$skip", pageable.getOffset()),
                new Document("$limit", size),
                new Document("$project", fields)))
                .append("count", List.of(new Document("$count", "total")));

        var pipeline = new ArrayList<AggregationOperation>();
        if (shelf == Shelf.BESTSELLING) {
            pipeline.addAll(bestSellerStages());
        }
        pipeline.add(stage("$match", match));
        pipeline.add(stage("$set", new Document("productRef", new Document("$toString", "$_id"))));
        pipeline.add(stage("$lookup", lookup));
        pipeline.add(stage("$unwind", "$prices"));
        pipeline.add(stage("$facet", facet));
        Class<?> source = shelf == Shelf.BESTSELLING ? OrderEntity.class : ProductEntity.class;
        Document result = mongo.aggregate(Aggregation.newAggregation(pipeline), mongo.getCollectionName(source),
                Document.class).getUniqueMappedResult();
        if (result == null) {
            return PageData.from(new PageImpl<>(List.of(), pageable, 0));
        }
        List<Product> items = result.getList("items", Document.class).stream().map(this::product).toList();
        List<Document> count = result.getList("count", Document.class);
        long total = count.isEmpty() ? 0 : count.getFirst().get("total", Number.class).longValue();
        return PageData.from(new PageImpl<>(items, pageable, total));
    }

    private List<AggregationOperation> bestSellerStages() {
        Date cutoff = Date.from(Instant.now().minus(30, ChronoUnit.DAYS));
        return List.of(
                stage("$match", new Document("status", "COMPLETED").append("paymentStatus", "PAID")
                        .append("completedAt", new Document("$gte", cutoff))),
                stage("$set", new Document("orderRef", new Document("$toString", "$_id"))),
                stage("$lookup", new Document("from", mongo.getCollectionName(OrderDetailEntity.class))
                        .append("localField", "orderRef").append("foreignField", "orderId").append("as", "line")),
                stage("$unwind", "$line"),
                stage("$match", new Document("line.productId", new Document("$type", "string"))
                        .append("line.quantity", new Document("$gt", 0))),
                stage("$group", new Document("_id", "$line.productId")
                        .append("soldQuantity", new Document("$sum", "$line.quantity"))),
                stage("$set", new Document("productIds", List.of("$_id", new Document("$convert",
                        new Document("input", "$_id").append("to", "objectId").append("onError", "$_id"))))),
                stage("$lookup", new Document("from", mongo.getCollectionName(ProductEntity.class))
                        .append("localField", "productIds").append("foreignField", "_id").append("as", "product")),
                stage("$unwind", "$product"),
                stage("$replaceWith", new Document("$mergeObjects",
                        List.of("$product", new Document("soldQuantity", "$soldQuantity")))));
    }

    private static AggregationOperation stage(String name, Object value) {
        return context -> new Document(name, value);
    }

    @Override
    public ProductDetail productDetail(String slug) {
        ProductEntity product = Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("slug").is(slug).and("status").is(ACTIVE)), ProductEntity.class))
                .orElseThrow(() -> new ApiException(ApiError.RESOURCE_NOT_FOUND));

        if (!visibleCategoryIds().contains(product.getCategoryId())) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }

        Query variantQuery = Query.query(Criteria.where("productId").is(product.getId()).and("status").is(ACTIVE)
                        .and("sellingPrice").gte(new Decimal128(BigDecimal.ZERO)))
                .with(Sort.by("netQuantity", "sku"));
        List<Variant> variants = mongo.find(variantQuery, ProductVariantEntity.class).stream()
                .map(variant -> new Variant(variant.getId(), variant.getSku(), variant.getName(),
                        variant.getNetQuantity(), variant.getUnitCode(), variant.getSellingPrice(),
                        variant.getCompareAtPrice()))
                .toList();
        if (variants.isEmpty()) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }

        List<ProductImage> images = Optional.ofNullable(product.getImages()).orElseGet(List::of).stream()
                .sorted(Comparator.comparing(ProductEntity.Image::primary).reversed()
                        .thenComparingInt(ProductEntity.Image::sortOrder))
                .map(image -> new ProductImage(image.url(), image.altText(), image.sortOrder(), image.primary()))
                .toList();
        return new ProductDetail(product.getId(), product.getProductCode(), product.getSlug(), product.getName(),
                product.getCategoryId(), product.getBrandName(), product.getOriginDescription(),
                product.getDescription(), product.getStorageInstructions(), product.isFeatured(), images, variants);
    }

    @Override
    public Set<String> visibleCategoryIds() {
        Map<String, List<CategoryEntity>> children = groupChildren(activeCategories());
        return descendants(children.getOrDefault(null, List.of()), children);
    }

    private List<CategoryEntity> activeCategories() {
        Query query = Query.query(Criteria.where("status").is(ACTIVE))
                .with(Sort.by("sortOrder", "name", "_id"));
        return mongo.find(query, CategoryEntity.class);
    }

    private static Map<String, List<CategoryEntity>> groupChildren(List<CategoryEntity> categories) {
        Map<String, List<CategoryEntity>> children = new HashMap<>();
        for (CategoryEntity category : categories) {
            children.computeIfAbsent(category.getParentCategoryId(), ignored -> new ArrayList<>()).add(category);
        }
        return children;
    }

    private static List<Category> categoryTree(String parent, Map<String, List<CategoryEntity>> children) {
        return children.getOrDefault(parent, List.of()).stream()
                .map(category -> new Category(category.getId(), category.getName(), category.getSlug(),
                        category.getDescription(), category.getImageUrl(), categoryTree(category.getId(), children)))
                .toList();
    }

    private static Set<String> descendants(List<CategoryEntity> roots, Map<String, List<CategoryEntity>> children) {
        Set<String> ids = new HashSet<>();
        Queue<CategoryEntity> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            CategoryEntity category = pending.remove();
            if (ids.add(category.getId())) {
                pending.addAll(children.getOrDefault(category.getId(), List.of()));
            }
        }
        return ids;
    }

    private Product product(Document document) {
        List<ProductEntity.Image> images = document.getList("images", Document.class, List.of()).stream()
                .map(image -> mongo.readDocument(ProductEntity.Image.class, image)).toList();
        Document sale = document.get("sale", Document.class);
        boolean discounted = sale != null && sale.getBoolean("discounted", false);
        return new Product(document.getString("id"), document.getString("productCode"), document.getString("name"),
                document.getString("slug"), document.getString("categoryId"), document.getString("brandName"),
                document.getString("originDescription"), images, document.getBoolean("featured", false),
                decimal(document.get("minPrice")), decimal(document.get("maxPrice")),
                discounted ? decimal(sale.get("price")) : null, discounted ? decimal(sale.get("compareAtPrice")) : null);
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof Decimal128 decimal ? decimal.bigDecimalValue() : new BigDecimal(value.toString());
    }
}
