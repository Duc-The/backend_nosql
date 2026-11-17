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
import vn.ducthe.organic.dto.request.ReceivingPayloads.CancellationInput;
import vn.ducthe.organic.dto.request.ReceivingPayloads.ConfirmationInput;
import vn.ducthe.organic.dto.request.ReceivingPayloads.ReceiptInput;
import vn.ducthe.organic.dto.request.ReceivingPayloads.ReceiptStatus;
import vn.ducthe.organic.dto.request.ReceivingPayloads.SupplierInput;
import vn.ducthe.organic.dto.request.ReceivingPayloads.SupplierStatus;
import vn.ducthe.organic.dto.request.ReceivingPayloads.SupplierStatusInput;
import vn.ducthe.organic.dto.response.GoodsReceiptResponses.Detail;
import vn.ducthe.organic.dto.response.GoodsReceiptResponses.ReceivingVariant;
import vn.ducthe.organic.dto.response.GoodsReceiptResponses.Summary;
import vn.ducthe.organic.entity.SupplierEntity;
import vn.ducthe.organic.service.GoodsReceiptService;
import vn.ducthe.organic.service.SupplierService;

@RestController
@RequestMapping("/api/v1/staff/warehouse")
public class ReceivingController {
    private final SupplierService suppliers;
    private final GoodsReceiptService receipts;

    ReceivingController(SupplierService suppliers, GoodsReceiptService receipts) {
        this.suppliers = suppliers;
        this.receipts = receipts;
    }

    @GetMapping("/suppliers")
    ResponseEntity<ApiResponse<PageData<SupplierEntity>>> suppliers(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) SupplierStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(suppliers.list(q, status, page, size), "Lấy danh sách nhà cung cấp thành công", request);
    }

    @GetMapping("/suppliers/{id}")
    ResponseEntity<ApiResponse<SupplierEntity>> supplier(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(suppliers.get(id), "Lấy thông tin nhà cung cấp thành công", request);
    }

    @PostMapping("/suppliers")
    ResponseEntity<ApiResponse<SupplierEntity>> createSupplier(@Valid @RequestBody SupplierInput body,
            HttpServletRequest request) {
        return ApiResponse.created(suppliers.create(body), "Đã tạo nhà cung cấp", request);
    }

    @PutMapping("/suppliers/{id}")
    ResponseEntity<ApiResponse<SupplierEntity>> editSupplier(@PathVariable String id,
            @Valid @RequestBody SupplierInput body, HttpServletRequest request) {
        return ApiResponse.ok(suppliers.edit(id, body), "Đã cập nhật nhà cung cấp", request);
    }

    @PutMapping("/suppliers/{id}/status")
    ResponseEntity<ApiResponse<SupplierEntity>> supplierStatus(@PathVariable String id,
            @Valid @RequestBody SupplierStatusInput body, HttpServletRequest request) {
        return ApiResponse.ok(suppliers.status(id, body.status()), "Đã cập nhật trạng thái nhà cung cấp", request);
    }

    @GetMapping("/goods-receipts")
    ResponseEntity<ApiResponse<PageData<Summary>>> receipts(
            @RequestParam(required = false) @Size(max = 128) String supplierId,
            @RequestParam(required = false) ReceiptStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(receipts.list(supplierId, status, page, size), "Lấy danh sách phiếu nhập thành công", request);
    }

    @GetMapping("/variants")
    ResponseEntity<ApiResponse<PageData<ReceivingVariant>>> variants(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(receipts.variants(q, page, size), "Lấy danh sách quy cách nhập kho thành công", request);
    }

    @GetMapping("/goods-receipts/{id}")
    ResponseEntity<ApiResponse<Detail>> receipt(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(receipts.detail(id), "Lấy chi tiết phiếu nhập thành công", request);
    }

    @PostMapping("/goods-receipts")
    ResponseEntity<ApiResponse<Detail>> createReceipt(@CurrentJwt Jwt jwt,
            @Valid @RequestBody ReceiptInput body, HttpServletRequest request) {
        return ApiResponse.created(receipts.create(jwt.getSubject(), body), "Đã lập phiếu nhập nháp", request);
    }

    @PutMapping("/goods-receipts/{id}")
    ResponseEntity<ApiResponse<Detail>> editReceipt(@CurrentJwt Jwt jwt, @PathVariable String id,
            @Valid @RequestBody ReceiptInput body, HttpServletRequest request) {
        return ApiResponse.ok(receipts.edit(jwt.getSubject(), id, body), "Đã cập nhật phiếu nhập nháp", request);
    }

    @PostMapping("/goods-receipts/{id}/confirm")
    ResponseEntity<ApiResponse<Detail>> confirm(@CurrentJwt Jwt jwt, @PathVariable String id,
            @Valid @RequestBody(required = false) ConfirmationInput body, HttpServletRequest request) {
        return ApiResponse.ok(receipts.confirm(jwt.getSubject(), id, body == null ? null : body.note()),
                "Đã xác nhận phiếu nhập", request);
    }

    @PostMapping("/goods-receipts/{id}/cancel")
    ResponseEntity<ApiResponse<Detail>> cancel(@CurrentJwt Jwt jwt, @PathVariable String id,
            @Valid @RequestBody CancellationInput body, HttpServletRequest request) {
        return ApiResponse.ok(receipts.cancel(jwt.getSubject(), id, body.reason()), "Đã hủy phiếu nhập nháp", request);
    }
}
