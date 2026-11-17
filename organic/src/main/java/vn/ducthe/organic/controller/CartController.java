package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.annotation.CurrentJwt;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.dto.request.CartRequests.AddItem;
import vn.ducthe.organic.dto.request.CartRequests.DeliveryAddress;
import vn.ducthe.organic.dto.request.CartRequests.PromotionInput;
import vn.ducthe.organic.dto.request.CartRequests.Quantity;
import vn.ducthe.organic.dto.response.CartResponses;
import vn.ducthe.organic.service.CartService;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private static final String ID = "[a-zA-Z0-9_-]{1,128}";
    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    ResponseEntity<ApiResponse<CartResponses.Cart>> get(@CurrentJwt Jwt jwt, HttpServletRequest request) {
        return ApiResponse.ok(cart.get(jwt.getSubject()), "Lấy giỏ hàng thành công", request);
    }

    @PostMapping("/items")
    ResponseEntity<ApiResponse<CartResponses.Cart>> add(@CurrentJwt Jwt jwt,
            @Valid @RequestBody AddItem body, HttpServletRequest request) {
        return ApiResponse.ok(cart.update(jwt.getSubject(), body.variantId(), body.quantity(), true),
                "Đã thêm sản phẩm vào giỏ", request);
    }

    @PutMapping("/items/{variantId}")
    ResponseEntity<ApiResponse<CartResponses.Cart>> update(@CurrentJwt Jwt jwt,
            @PathVariable @Pattern(regexp = ID) String variantId,
            @Valid @RequestBody Quantity body, HttpServletRequest request) {
        return ApiResponse.ok(cart.update(jwt.getSubject(), variantId, body.quantity(), false),
                "Đã cập nhật số lượng", request);
    }

    @DeleteMapping("/items/{variantId}")
    ResponseEntity<Void> remove(@CurrentJwt Jwt jwt, @PathVariable @Pattern(regexp = ID) String variantId) {
        cart.remove(jwt.getSubject(), variantId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/delivery-address")
    ResponseEntity<ApiResponse<CartResponses.Cart>> selectAddress(@CurrentJwt Jwt jwt,
            @Valid @RequestBody DeliveryAddress body, HttpServletRequest request) {
        return ApiResponse.ok(cart.selectAddress(jwt.getSubject(), body.addressId()),
                "Đã chọn địa chỉ giao hàng", request);
    }

    @PutMapping("/promotion")
    ResponseEntity<ApiResponse<CartResponses.Cart>> applyPromotion(@CurrentJwt Jwt jwt,
            @Valid @RequestBody PromotionInput body, HttpServletRequest request) {
        return ApiResponse.ok(cart.applyPromotion(jwt.getSubject(), body.code()), "Đã áp mã giảm giá", request);
    }

    @DeleteMapping("/promotion")
    ResponseEntity<Void> removePromotion(@CurrentJwt Jwt jwt) {
        cart.removePromotion(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
