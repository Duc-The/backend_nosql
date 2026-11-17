package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Document(collection = "payments")
@CompoundIndex(name = "orderId_createdAt", def = "{'orderId': 1, 'createdAt': 1}")
@CompoundIndex(name = "status_currency_paidAt", def = "{'status': 1, 'currency': 1, 'paidAt': -1}")
@CompoundIndex(name = "paid_payment_per_order", def = "{'orderId': 1}", unique = true,
        partialFilter = "{'status': 'PAID'}")
@CompoundIndex(name = "method_gatewayTransactionId", def = "{'method': 1, 'gatewayTransactionId': 1}", unique = true,
        partialFilter = "{'gatewayTransactionId': {'$type': 'string'}}")
@CompoundIndex(name = "method_merchantReference", def = "{'method': 1, 'merchantReference': 1}", unique = true,
        partialFilter = "{'merchantReference': {'$type': 'string'}}")
@CompoundIndex(name = "orderId_idempotencyKey", def = "{'orderId': 1, 'idempotencyKey': 1}", unique = true,
        partialFilter = "{'idempotencyKey': {'$type': 'string'}}")
public class PaymentEntity extends BaseEntity {

    @Indexed(unique = true)
    private String paymentCode;
    private String orderId;
    private Method method;
    private Status status;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;
    private String currency = "VND";
    private String merchantReference;
    private String gatewayTransactionId;
    private String providerLinkId;
    private String idempotencyKey;
    private String paymentUrl;
    private String qrPayload;
    private String transferReference;
    private String confirmedByEmployeeId;
    private Instant expiresAt;
    private Instant paidAt;
    private String failureCode;
    private String failureMessage;

    @Version
    private Long version;

    public enum Method {
        COD, VNPAY, VIETQR
    }

    public enum Status {
        PENDING, PAID, FAILED, EXPIRED, CANCELLED
    }
}
