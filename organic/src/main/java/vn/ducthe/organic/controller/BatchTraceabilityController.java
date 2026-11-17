package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.BatchTraceabilityResponses.IssuedOrder;
import vn.ducthe.organic.dto.response.BatchTraceabilityResponses.TracedBatch;
import vn.ducthe.organic.service.BatchTraceabilityService;

@RestController
@RequestMapping("/api/v1/staff/warehouse/inventory/traceability")
public class BatchTraceabilityController {
    private final BatchTraceabilityService traceability;

    BatchTraceabilityController(BatchTraceabilityService traceability) {
        this.traceability = traceability;
    }

    @GetMapping
    ResponseEntity<ApiResponse<PageData<TracedBatch>>> lookup(
            @RequestParam @NotBlank @Size(max = 128) String code,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(traceability.lookup(code, page, size), "Tra cứu nguồn gốc lô thành công", request);
    }

    @GetMapping("/batches/{batchId}/orders")
    ResponseEntity<ApiResponse<PageData<IssuedOrder>>> orders(@PathVariable @Size(max = 128) String batchId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(traceability.issuedOrders(batchId, page, size), "Lấy danh sách đơn đã xuất lô thành công", request);
    }
}
