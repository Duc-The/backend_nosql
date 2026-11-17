package vn.ducthe.organic.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.inventory.BatchAvailability;
import vn.ducthe.organic.dto.response.CartResponses.Cart;
import vn.ducthe.organic.dto.response.CartResponses.Item;
import vn.ducthe.organic.entity.CustomerAddressEntity;
import vn.ducthe.organic.entity.OrderEntity.AddressSnapshot;
import vn.ducthe.organic.entity.ProductBatchEntity;
import vn.ducthe.organic.entity.ProductEntity;
import vn.ducthe.organic.entity.ProductVariantEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.repository.RedisCart;
import vn.ducthe.organic.service.CartService;
import vn.ducthe.organic.service.CatalogService;
import vn.ducthe.organic.service.PromotionService;

@Service
public class CartServiceImpl implements CartService {
    private final RedisCart redis;
    private final MongoDocumentRepository mongo;
    private final AuthStore auth;
    private final CatalogService catalog;
    private final PromotionService promotions;
    private final BigDecimal shippingFee;

    public CartServiceImpl(RedisCart redis, MongoDocumentRepository mongo, AuthStore auth, CatalogService catalog, PromotionService promotions,
                @Value("${app.cart.shipping-fee:25000}") BigDecimal shippingFee) {
        this.redis = redis;
        this.mongo = mongo;
        this.auth = auth;
        this.catalog = catalog;
        this.promotions = promotions;
        this.shippingFee = shippingFee;
        if (shippingFee.signum() < 0) {
            throw new IllegalArgumentException("app.cart.shipping-fee cannot be negative");
        }
    }

    @Override
    public Cart get(String accountId) {
        return view(customerId(accountId));
    }

    @Override
    public Cart update(String accountId, String variantId, int quantity, boolean add) {
        String customerId = customerId(accountId);
        CatalogSnapshot snapshot = loadCatalog(List.of(variantId));
        ProductVariantEntity variant = snapshot.variants().get(variantId);
        if (variant == null || !snapshot.products().containsKey(variant.getProductId())) {
            throw new ApiException(ApiError.CART_ITEM_UNAVAILABLE);
        }
        int available = snapshot.stock().getOrDefault(variantId, BigDecimal.ZERO)
                .min(BigDecimal.valueOf(99)).intValue();
        redis.update(customerId, variantId, quantity, available, add);
        return view(customerId);
    }

    @Override
    public void remove(String accountId, String variantId) {
        redis.remove(customerId(accountId), variantId);
    }

    @Override
    public Cart selectAddress(String accountId, String addressId) {
        String customerId = customerId(accountId);
        if (address(customerId, addressId) == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        redis.selectAddress(customerId, addressId);
        return view(customerId);
    }

    private Cart view(String customerId) {
        return checkoutCart(customerId).cart();
    }

    @Override
    public Cart applyPromotion(String accountId, String code) {
        String customerId = customerId(accountId);
        Cart current = view(customerId);
        var quote = promotions.preview(customerId, code, current.subtotalAmount(), priceLines(current.items()),
                Instant.now());
        redis.setPromotion(customerId, quote.code());
        return view(customerId);
    }

    @Override
    public void removePromotion(String accountId) {
        redis.setPromotion(customerId(accountId), null);
    }

    private CheckoutCart checkoutCart(String customerId) {
        return checkoutCart(customerId, Instant.now());
    }

    @Override
    public CheckoutCart checkoutCart(String customerId, Instant now) {
        RedisCart.Snapshot saved = redis.read(customerId);
        CustomerAddressEntity address = address(customerId, saved.addressId());
        CatalogSnapshot snapshot = loadCatalog(saved.quantities().keySet());
        List<Item> items = saved.quantities().entrySet().stream()
                .map(entry -> item(entry.getKey(), entry.getValue(), snapshot))
                .sorted(Comparator.comparing(Item::variantId)).toList();
        BigDecimal subtotal = items.stream().map(Item::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fee = address == null || items.isEmpty() ? BigDecimal.ZERO : shippingFee;
        PromotionService.Quote promotion = null;
        ApiError promotionError = null;
        if (saved.promotionCode() != null) {
            try {
                promotion = promotions.preview(customerId, saved.promotionCode(), subtotal, priceLines(items), now);
            } catch (ApiException exception) {
                promotionError = exception.error();
            }
        }
        BigDecimal discount = promotion == null ? BigDecimal.ZERO : promotion.discount();
        boolean canCheckout = address != null && !items.isEmpty() && promotionError == null
                && items.stream().allMatch(Item::available);
        Cart cart = new Cart(items, address == null ? null : address.getId(), "VND",
                subtotal, fee, saved.promotionCode(), discount,
                promotion == null ? null : promotion.promotion().getTarget(),
                promotion == null ? BigDecimal.ZERO : promotion.eligibleAmount(),
                promotionError == null ? null : promotionError.name(),
                subtotal.subtract(discount).add(fee), canCheckout);
        AddressSnapshot shippingAddress = address == null ? null : new AddressSnapshot(address.getReceiverName(),
                address.getReceiverPhone(), address.getProvince(), address.getDistrict(), address.getWard(),
                address.getStreetAddress());
        return new CheckoutCart(cart, shippingAddress, saved.revision(), promotion, promotionError);
    }

    @Override
    public void clearAfterCheckout(String customerId, String revision) {
        redis.clearIfUnchanged(customerId, revision);
    }

    private static List<PromotionService.PriceLine> priceLines(List<Item> items) {
        return items.stream().filter(item -> item.unitPrice() != null && item.productId() != null)
                .map(item -> new PromotionService.PriceLine(item.productId(), item.variantId(),
                        BigDecimal.valueOf(item.quantity()), item.unitPrice())).toList();
    }

    private Item item(String variantId, int quantity, CatalogSnapshot snapshot) {
        ProductVariantEntity variant = snapshot.variants().get(variantId);
        ProductEntity product = variant == null ? null : snapshot.products().get(variant.getProductId());
        if (product == null) {
            return new Item(variantId, null, null, null, null, null, null, quantity, null, BigDecimal.ZERO,
                    BigDecimal.ZERO, false, null);
        }
        BigDecimal available = snapshot.stock().getOrDefault(variantId, BigDecimal.ZERO);
        BigDecimal units = BigDecimal.valueOf(quantity);
        return new Item(variantId, product.getId(), product.getSlug(), product.getName(), variant.getSku(),
                variant.getName(), variant.getUnitCode(), quantity, variant.getSellingPrice(), variant.getSellingPrice().multiply(units),
                available, available.compareTo(units) >= 0, imageUrl(product));
    }

    private static String imageUrl(ProductEntity product) {
        if (product.getImages() == null) {
            return null;
        }
        return product.getImages().stream()
                .filter(image -> image != null && image.url() != null && !image.url().isBlank())
                .sorted(Comparator.comparing(ProductEntity.Image::primary).reversed()
                        .thenComparingInt(ProductEntity.Image::sortOrder))
                .map(ProductEntity.Image::url)
                .findFirst().orElse(null);
    }

    private CatalogSnapshot loadCatalog(Collection<String> ids) {
        if (ids.isEmpty()) {
            return new CatalogSnapshot(Map.of(), Map.of(), Map.of());
        }
        Query variantQuery = Query.query(Criteria.where("_id").in(ids).and("status").is(AuthStore.ACTIVE)
                .and("sellingPrice").gte(new Decimal128(BigDecimal.ZERO)));
        Map<String, ProductVariantEntity> variants = mongo.find(variantQuery, ProductVariantEntity.class).stream()
                .collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
        if (variants.isEmpty()) {
            return new CatalogSnapshot(variants, Map.of(), Map.of());
        }
        Set<String> productIds = variants.values().stream().map(ProductVariantEntity::getProductId)
                .collect(Collectors.toSet());
        Query productQuery = Query.query(Criteria.where("_id").in(productIds).and("status").is(AuthStore.ACTIVE)
                .and("categoryId").in(catalog.visibleCategoryIds()));
        Map<String, ProductEntity> products = mongo.find(productQuery, ProductEntity.class).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        return new CatalogSnapshot(variants, products, stock(variants.keySet()));
    }

    private Map<String, BigDecimal> stock(Set<String> variantIds) {
        Criteria criteria = BatchAvailability.sellableBatches(variantIds, BatchAvailability.shopDate(Instant.now()));
        Aggregation query = Aggregation.newAggregation(ProductBatchEntity.class, Aggregation.match(criteria),
                context -> new Document("$group", new Document("_id", "$variantId")
                        .append("available", new Document("$sum", BatchAvailability.unreservedExpression()))));
        return mongo.aggregate(query, ProductBatchEntity.class, Document.class).getMappedResults().stream()
                .collect(Collectors.toMap(row -> row.getString("_id"), row -> {
                    Object value = row.get("available");
                    return value instanceof Decimal128 decimal ? decimal.bigDecimalValue() : new BigDecimal(value.toString());
                }));
    }

    private CustomerAddressEntity address(String customerId, String addressId) {
        if (addressId == null) {
            return null;
        }
        return mongo.findOne(Query.query(Criteria.where("_id").is(addressId).and("customerId").is(customerId)
                .and("status").is(AuthStore.ACTIVE)), CustomerAddressEntity.class);
    }

    private String customerId(String accountId) {
        return auth.customer(accountId).getId();
    }

    private record CatalogSnapshot(Map<String, ProductVariantEntity> variants, Map<String, ProductEntity> products,
                                   Map<String, BigDecimal> stock) {}
}
