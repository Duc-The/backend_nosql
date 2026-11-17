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
import vn.ducthe.organic.dto.request.InventoryRequests.StatusInput;
import vn.ducthe.organic.dto.response.InventoryResponses.Batch;
import vn.ducthe.organic.dto.response.InventoryResponses.VariantStock;
import vn.ducthe.organic.service.InventoryService;
import vn.ducthe.organic.service.InventoryService.BatchStatus;
import vn.ducthe.organic.service.InventoryService.Expiry;

@RestController
@RequestMapping("/api/v1/staff/warehouse/inventory")
public class InventoryController {
    private final InventoryService inventory;

    InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/batches")
    ResponseEntity<ApiResponse<PageData<Batch>>> batches(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 128) String variantId,
            @RequestParam(required = false) BatchStatus status,
            @RequestParam(required = false) Expiry expiry,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(inventory.batches(q, variantId, status, expiry, inStockOnly, page, size),
                "Lấy danh sách lô hàng thành công", request);
    }

    @GetMapping("/batches/{id}")
    ResponseEntity<ApiResponse<Batch>> batch(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(inventory.detail(id), "Lấy thông tin lô hàng thành công", request);
    }

    @PutMapping("/batches/{id}/status")
    ResponseEntity<ApiResponse<Batch>> status(@PathVariable String id, @CurrentJwt Jwt jwt,
            @Valid @RequestBody StatusInput body, HttpServletRequest request) {
        return ApiResponse.ok(inventory.status(id, jwt.getSubject(), body.status(), body.reason()),
                "Đã cập nhật trạng thái lô", request);
    }

    @GetMapping("/sku-stock")
    ResponseEntity<ApiResponse<PageData<VariantStock>>> skuStock(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(inventory.skuStock(q, lowStock, page, size), "Lấy tồn kho theo SKU thành công", request);
    }
}
