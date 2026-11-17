package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.PromotionAdminPayloads.Input;
import vn.ducthe.organic.dto.request.PromotionAdminPayloads.Status;
import vn.ducthe.organic.dto.request.PromotionAdminPayloads.StatusInput;
import vn.ducthe.organic.dto.request.PromotionAdminPayloads.VoucherInput;
import vn.ducthe.organic.dto.response.PromotionAdminResponses.Detail;
import vn.ducthe.organic.entity.CustomerVoucherEntity;
import vn.ducthe.organic.entity.PromotionEntity;
import vn.ducthe.organic.service.PromotionAdminService;

@RestController
@RequestMapping("/api/v1/admin/promotions")
public class PromotionAdminController {
    private final PromotionAdminService promotions;

    PromotionAdminController(PromotionAdminService promotions) {
        this.promotions = promotions;
    }

    @GetMapping
    ResponseEntity<ApiResponse<PageData<PromotionEntity>>> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) PromotionEntity.Target target,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(promotions.list(q, target, status, page, size), "Lấy danh sách khuyến mãi thành công", request);
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<Detail>> detail(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(promotions.detail(id), "Lấy chi tiết khuyến mãi thành công", request);
    }

    @PostMapping
    ResponseEntity<ApiResponse<Detail>> create(@Valid @RequestBody Input body, HttpServletRequest request) {
        return ApiResponse.created(promotions.create(body), "Đã tạo khuyến mãi", request);
    }

    @PutMapping("/{id}")
    ResponseEntity<ApiResponse<Detail>> edit(@PathVariable String id, @Valid @RequestBody Input body,
            HttpServletRequest request) {
        return ApiResponse.ok(promotions.edit(id, body), "Đã cập nhật khuyến mãi", request);
    }

    @PutMapping("/{id}/status")
    ResponseEntity<ApiResponse<Detail>> status(@PathVariable String id, @Valid @RequestBody StatusInput body,
            HttpServletRequest request) {
        return ApiResponse.ok(promotions.status(id, body.status()), "Đã cập nhật trạng thái khuyến mãi", request);
    }

    @GetMapping("/{id}/vouchers")
    ResponseEntity<ApiResponse<PageData<CustomerVoucherEntity>>> vouchers(@PathVariable String id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(promotions.vouchers(id, page, size), "Lấy danh sách voucher đã cấp thành công", request);
    }

    @PostMapping("/{id}/vouchers")
    ResponseEntity<ApiResponse<CustomerVoucherEntity>> issue(@PathVariable String id, @Valid @RequestBody VoucherInput body,
            HttpServletRequest request) {
        return ApiResponse.created(promotions.issueVoucher(id, body), "Đã cấp voucher cá nhân", request);
    }
}
