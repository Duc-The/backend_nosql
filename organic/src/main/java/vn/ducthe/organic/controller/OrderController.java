package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.annotation.CurrentJwt;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.OrderRequests.Cancel;
import vn.ducthe.organic.dto.request.OrderRequests.Checkout;
import vn.ducthe.organic.dto.response.OrderResponses;
import vn.ducthe.organic.service.OrderService;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    ResponseEntity<ApiResponse<OrderResponses.Detail>> checkout(
            @CurrentJwt Jwt jwt,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{1,128}") String key,
            @Valid @RequestBody Checkout body, HttpServletRequest request) {
        var result = orders.checkout(jwt.getSubject(), key, body.note(), body.paymentMethod());
        if (result.created()) {
            return ApiResponse.created(result.order(), "Đặt hàng thành công", request);
        }
        return ApiResponse.ok(result.order(), "Đơn hàng đã được tạo", request);
    }

    @GetMapping
    ResponseEntity<ApiResponse<PageData<OrderResponses.Summary>>> list(
            @CurrentJwt Jwt jwt, @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(orders.list(jwt.getSubject(), page, size), "Lấy lịch sử đơn hàng thành công", request);
    }

    @GetMapping("/{orderCode}")
    ResponseEntity<ApiResponse<OrderResponses.Detail>> detail(
            @CurrentJwt Jwt jwt, @PathVariable @Size(max = 128) String orderCode,
            HttpServletRequest request) {
        return ApiResponse.ok(orders.detail(jwt.getSubject(), orderCode), "Lấy chi tiết đơn hàng thành công", request);
    }

    @PostMapping("/{orderCode}/cancel")
    ResponseEntity<ApiResponse<OrderResponses.Detail>> cancel(
            @CurrentJwt Jwt jwt, @PathVariable @Size(max = 128) String orderCode,
            @Valid @RequestBody Cancel body, HttpServletRequest request) {
        return ApiResponse.ok(orders.cancel(jwt.getSubject(), orderCode, body.reason()), "Đã hủy đơn hàng", request);
    }
}
