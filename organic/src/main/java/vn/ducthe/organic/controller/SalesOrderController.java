package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.annotation.CurrentJwt;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.SalesOrderRequests.AssignmentInput;
import vn.ducthe.organic.dto.request.SalesOrderRequests.StatusInput;
import vn.ducthe.organic.dto.response.SalesOrderResponses.Detail;
import vn.ducthe.organic.dto.response.SalesOrderResponses.Row;
import vn.ducthe.organic.service.SalesOrderService;
import vn.ducthe.organic.service.SalesOrderService.Status;

@RestController
@RequestMapping("/api/v1/staff/orders")
public class SalesOrderController {
    private final SalesOrderService orders;

    SalesOrderController(SalesOrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    ResponseEntity<ApiResponse<PageData<Row>>> list(@CurrentJwt Jwt jwt,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) @Size(max = 128) String assignedEmployeeId,
            @RequestParam(required = false) @Size(max = 128) String q,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(orders.list(jwt.getSubject(), status, assignedEmployeeId, q, page, size),
                "Lấy danh sách đơn hàng thành công", request);
    }

    @GetMapping("/{orderCode}")
    ResponseEntity<ApiResponse<Detail>> detail(@CurrentJwt Jwt jwt,
            @PathVariable @Size(max = 128) String orderCode, HttpServletRequest request) {
        return ApiResponse.ok(orders.detail(jwt.getSubject(), orderCode), "Lấy chi tiết đơn hàng thành công", request);
    }

    @PutMapping("/{orderCode}/assignment")
    ResponseEntity<ApiResponse<Detail>> assign(@CurrentJwt Jwt jwt,
            @PathVariable @Size(max = 128) String orderCode, @Valid @RequestBody AssignmentInput body,
            HttpServletRequest request) {
        return ApiResponse.ok(orders.assign(jwt.getSubject(), orderCode, body.employeeId()),
                "Đã cập nhật người phụ trách đơn", request);
    }

    @PutMapping("/{orderCode}/status")
    ResponseEntity<ApiResponse<Detail>> status(@CurrentJwt Jwt jwt,
            @PathVariable @Size(max = 128) String orderCode, @Valid @RequestBody StatusInput body,
            HttpServletRequest request) {
        return ApiResponse.ok(orders.transition(jwt.getSubject(), orderCode, body.status(), body.carrierName(),
                body.trackingCode()), "Đã cập nhật trạng thái đơn", request);
    }
}
