package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.annotation.CurrentJwt;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.OrderResponses.WarehouseOrder;
import vn.ducthe.organic.dto.response.OrderResponses.WarehouseQueueItem;
import vn.ducthe.organic.service.OrderService;

@RestController
@RequestMapping("/api/v1/staff/warehouse/inventory/orders")
public class WarehouseOrderController {
    private final OrderService orders;

    WarehouseOrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    ResponseEntity<ApiResponse<PageData<WarehouseQueueItem>>> queue(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(orders.warehouseQueue(page, size), "Lấy danh sách đơn chờ xuất kho thành công", request);
    }

    @GetMapping("/{orderCode}")
    ResponseEntity<ApiResponse<WarehouseOrder>> detail(@PathVariable @Size(max = 128) String orderCode,
            HttpServletRequest request) {
        return ApiResponse.ok(orders.warehouseOrder(orderCode), "Lấy thông tin phân bổ lô thành công", request);
    }

    @PostMapping("/{orderCode}/issue")
    ResponseEntity<ApiResponse<WarehouseOrder>> issue(@PathVariable @Size(max = 128) String orderCode,
            @CurrentJwt Jwt jwt, HttpServletRequest request) {
        return ApiResponse.ok(orders.issue(orderCode, jwt.getSubject()), "Đã xuất kho cho đơn hàng", request);
    }
}
