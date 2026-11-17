package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ducthe.organic.annotation.CurrentJwt;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.LoyaltyResponses.Balance;
import vn.ducthe.organic.dto.response.LoyaltyResponses.PointEntry;
import vn.ducthe.organic.dto.response.LoyaltyResponses.Voucher;
import vn.ducthe.organic.service.LoyaltyService;

@RestController
@RequestMapping("/api/v1/customers/me")
public class LoyaltyController {
    private final LoyaltyService loyalty;

    public LoyaltyController(LoyaltyService loyalty) {
        this.loyalty = loyalty;
    }

    @GetMapping("/points")
    ResponseEntity<ApiResponse<Balance>> balance(@CurrentJwt Jwt jwt, HttpServletRequest request) {
        return ApiResponse.ok(loyalty.balance(jwt.getSubject()), "Lấy thông tin điểm thưởng thành công", request);
    }

    @GetMapping("/points/transactions")
    ResponseEntity<ApiResponse<PageData<PointEntry>>> transactions(@CurrentJwt Jwt jwt,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(loyalty.history(jwt.getSubject(), page, size), "Lấy lịch sử điểm thành công", request);
    }

    @GetMapping("/vouchers")
    ResponseEntity<ApiResponse<PageData<Voucher>>> vouchers(@CurrentJwt Jwt jwt,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(loyalty.vouchers(jwt.getSubject(), page, size), "Lấy voucher cá nhân thành công", request);
    }

    @PostMapping("/vouchers/redeem")
    ResponseEntity<ApiResponse<Voucher>> redeem(@CurrentJwt Jwt jwt,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{1,128}") String key,
            HttpServletRequest request) {
        var result = loyalty.redeem(jwt.getSubject(), key);
        if (result.created()) {
            return ApiResponse.created(result.voucher(), "Đổi điểm thành công", request);
        }
        return ApiResponse.ok(result.voucher(), "Voucher đã được cấp", request);
    }
}
