package vn.ducthe.organic.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import vn.ducthe.organic.entity.PaymentEntity.Method;
import vn.ducthe.organic.entity.PaymentEntity.Status;

public final class PaymentResponses {
    private PaymentResponses() {}

    public record PaymentView(String paymentCode, Method method, Status status, BigDecimal amount, String currency,
                              String paymentUrl, String qrPayload, Instant expiresAt, Instant paidAt, String failureCode) {}
}
