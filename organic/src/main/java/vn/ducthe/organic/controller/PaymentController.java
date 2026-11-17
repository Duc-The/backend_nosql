package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.annotation.CurrentJwt;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.dto.response.PaymentResponses.PaymentView;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.service.PaymentService;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/orders/{orderCode}/payment")
    ResponseEntity<ApiResponse<PaymentView>> payment(@CurrentJwt Jwt jwt,
            @PathVariable @Size(max = 128) String orderCode, HttpServletRequest request) {
        return ApiResponse.ok(payments.get(jwt.getSubject(), orderCode), "Lấy thông tin thanh toán thành công", request);
    }

    @PostMapping("/orders/{orderCode}/payment")
    ResponseEntity<ApiResponse<PaymentView>> start(@CurrentJwt Jwt jwt,
            @PathVariable @Size(max = 128) String orderCode, HttpServletRequest request) {
        return ApiResponse.ok(payments.start(jwt.getSubject(), orderCode, request.getRemoteAddr()),
                "Lấy thông tin thanh toán thành công", request);
    }

    @PostMapping("/orders/{orderCode}/payment/vnpay-result")
    ResponseEntity<ApiResponse<Map<String, String>>> vnpayResult(@CurrentJwt Jwt jwt,
            @PathVariable @Size(max = 128) String orderCode, @RequestBody Map<String, String> fields,
            HttpServletRequest request) {
        String status = payments.verifyVnpayReturn(jwt.getSubject(), orderCode, fields);
        return ApiResponse.ok(Map.of("status", status), "Đã kiểm tra kết quả VNPAY", request);
    }

    @PostMapping("/staff/orders/{orderCode}/payment/confirm-cod")
    ResponseEntity<ApiResponse<PaymentView>> confirm(@CurrentJwt Jwt jwt,
            @PathVariable @Size(max = 128) String orderCode, HttpServletRequest request) {
        return ApiResponse.ok(payments.confirmCod(jwt.getSubject(), orderCode), "Đã xác nhận thu tiền COD", request);
    }

    @GetMapping("/payments/vnpay/ipn")
    Map<String, String> vnpay(@RequestParam MultiValueMap<String, String> parameters) {
        Map<String, String> fields = new HashMap<>();
        for (var parameter : parameters.entrySet()) {
            if (!parameter.getKey().startsWith("vnp_")) {
                continue;
            }
            if (parameter.getValue().size() != 1) {
                return vnpResponse("97", "Invalid signature");
            }
            fields.put(parameter.getKey(), parameter.getValue().getFirst());
        }
        try {
            return switch (payments.vnpay(fields)) {
                case ACCEPTED -> vnpResponse("00", "Confirm success");
                case ALREADY_CONFIRMED -> vnpResponse("02", "Order already confirmed");
                case NOT_FOUND -> vnpResponse("01", "Order not found");
                case INVALID_AMOUNT -> vnpResponse("04", "Invalid amount");
                case INVALID_SIGNATURE -> vnpResponse("97", "Invalid signature");
                case INVALID_DATA -> vnpResponse("99", "Invalid transaction data");
            };
        } catch (RuntimeException exception) {
            log.error("VNPAY IPN processing failed", exception);
            return vnpResponse("99", "Internal error");
        }
    }

    @PostMapping("/payments/payos/webhook")
    ResponseEntity<Void> payos(@RequestBody Map<String, Object> payload) {
        switch (payments.payos(payload)) {
            case INVALID_SIGNATURE -> throw new ApiException(ApiError.INVALID_PAYMENT_SIGNATURE);
            case INVALID_DATA -> throw new ApiException(ApiError.INVALID_PAYMENT_NOTIFICATION);
            // Acknowledge signed verification samples and discrepancies already recorded for reconciliation.
            default -> { return ResponseEntity.ok().build(); }
        }
    }

    private static Map<String, String> vnpResponse(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }
}
