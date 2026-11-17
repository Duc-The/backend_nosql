package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.InventoryResponses.Batch;
import vn.ducthe.organic.dto.response.InventoryResponses.VariantStock;
import vn.ducthe.organic.dto.response.ReportingResponses;
import vn.ducthe.organic.service.InventoryService;
import vn.ducthe.organic.service.ReportingService;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportingController {
    private final ReportingService reports;
    private final InventoryService inventory;

    ReportingController(ReportingService reports, InventoryService inventory) {
        this.reports = reports;
        this.inventory = inventory;
    }

    @GetMapping("/sales")
    ResponseEntity<ApiResponse<ReportingResponses.Sales>> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        return ApiResponse.ok(reports.sales(from, to), "Lấy báo cáo doanh thu và đơn hàng thành công", request);
    }

    @GetMapping("/top-skus")
    ResponseEntity<ApiResponse<ReportingResponses.TopSkus>> topSkus(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit, HttpServletRequest request) {
        return ApiResponse.ok(reports.topSkus(from, to, limit), "Lấy báo cáo SKU bán chạy thành công", request);
    }

    @GetMapping("/inventory/sku-stock")
    ResponseEntity<ApiResponse<PageData<VariantStock>>> skuStock(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(inventory.skuStock(q, lowStock, page, size), "Lấy báo cáo tồn kho thành công", request);
    }

    @GetMapping("/inventory/expiring-batches")
    ResponseEntity<ApiResponse<PageData<Batch>>> expiringBatches(
            @RequestParam(required = false) @Size(max = 128) String variantId,
            @RequestParam(defaultValue = "false") boolean expired,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(inventory.expiryReport(variantId, expired, page, size),
                "Lấy báo cáo hạn dùng thành công", request);
    }
}
