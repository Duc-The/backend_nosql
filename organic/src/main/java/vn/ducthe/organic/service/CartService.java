package vn.ducthe.organic.service;

import java.time.Instant;
import vn.ducthe.organic.dto.response.CartResponses.Cart;
import vn.ducthe.organic.entity.OrderEntity.AddressSnapshot;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.service.PromotionService;

public interface CartService {

    Cart get(String accountId);

    Cart update(String accountId, String variantId, int quantity, boolean add);

    void remove(String accountId, String variantId);

    Cart selectAddress(String accountId, String addressId);

    Cart applyPromotion(String accountId, String code);

    void removePromotion(String accountId);

    CheckoutCart checkoutCart(String customerId, Instant now);

    void clearAfterCheckout(String customerId, String revision);

    record CheckoutCart(Cart cart, AddressSnapshot shippingAddress, String revision,
                               PromotionService.Quote promotion, ApiError promotionError) {}
}
