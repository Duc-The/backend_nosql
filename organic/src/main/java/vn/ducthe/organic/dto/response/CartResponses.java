package vn.ducthe.organic.dto.response;

import java.math.BigDecimal;
import java.util.List;
import vn.ducthe.organic.entity.PromotionEntity.Target;

public final class CartResponses {
    private CartResponses() {}

    public record Cart(List<Item> items, String addressId, String currency, BigDecimal subtotalAmount,
                       BigDecimal shippingFee, String promotionCode, BigDecimal promotionDiscount,
                       Target promotionTarget, BigDecimal promotionEligibleAmount,
                       String promotionError, BigDecimal totalAmount, boolean canCheckout) {}

    public record Item(String variantId, String productId, String slug, String productName, String sku,
                       String variantName, String unitCode, int quantity, BigDecimal unitPrice, BigDecimal lineTotal,
                       BigDecimal availableQuantity, boolean available, String imageUrl) {}
}
