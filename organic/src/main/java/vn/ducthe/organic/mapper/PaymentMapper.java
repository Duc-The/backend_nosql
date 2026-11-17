package vn.ducthe.organic.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import vn.ducthe.organic.dto.response.PaymentResponses.PaymentView;
import vn.ducthe.organic.entity.PaymentEntity;
import vn.ducthe.organic.entity.PaymentEntity.Status;
import vn.ducthe.organic.entity.PaymentEventEntity;

public final class PaymentMapper {
    private PaymentMapper() {}

    public static PaymentView view(PaymentEntity payment) {
        boolean showLink = payment.getStatus() == Status.PENDING && payment.getExpiresAt() != null
                && Instant.now().isBefore(payment.getExpiresAt());
        return new PaymentView(payment.getPaymentCode(), payment.getMethod(), payment.getStatus(), payment.getAmount(),
                payment.getCurrency(), showLink ? payment.getPaymentUrl() : null, showLink ? payment.getQrPayload() : null,
                payment.getExpiresAt(), payment.getPaidAt(), payment.getFailureCode());
    }

    public static PaymentEventEntity event(PaymentEntity payment, String source, String eventKey, String type,
                                            String transaction, BigDecimal amount, Instant now) {
        PaymentEventEntity event = new PaymentEventEntity();
        event.setPaymentId(payment.getId());
        event.setSource(source);
        event.setEventKey(eventKey);
        event.setEventType(type);
        event.setProviderTransactionId(transaction);
        event.setReportedAmount(amount);
        event.setCurrency(payment.getCurrency());
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        event.setReceivedAt(now);
        event.setProcessedAt(now);
        return event;
    }
}
