package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.AdminCustomerResponses.CustomerRow;
import vn.ducthe.organic.service.AdminCustomerService;

@RestController
@RequestMapping("/api/v1/admin/customers")
public class AdminCustomerController {
    private final AdminCustomerService customers;

    AdminCustomerController(AdminCustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    ResponseEntity<ApiResponse<PageData<CustomerRow>>> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(customers.list(q, activeOnly, page, size),
                "Lấy danh sách khách hàng thành công", request);
    }
}
