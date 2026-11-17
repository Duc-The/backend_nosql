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
@Document(collection = "orders")
@CompoundIndex(name = "customerId_orderedAt", def = "{'customerId': 1, 'orderedAt': 1}")
@CompoundIndex(name = "status_orderedAt", def = "{'status': 1, 'orderedAt': 1}")
@CompoundIndex(name = "status_paymentDueAt", def = "{'status': 1, 'paymentDueAt': 1}")
@CompoundIndex(name = "status_stockReservedUntil", def = "{'status': 1, 'stockReservedUntil': 1}")
@CompoundIndex(name = "employee_status_orderedAt", def = "{'assignedEmployeeId': 1, 'status': 1, 'orderedAt': -1}")
@CompoundIndex(name = "loyalty_earn_queue", def = "{'status': 1, 'paymentStatus': 1, 'pointsProcessedAt': 1, 'completedAt': 1}")
@CompoundIndex(name = "customerId_checkoutKey", def = "{'customerId': 1, 'checkoutKey': 1}", unique = true,
        partialFilter = "{'checkoutKey': {'$type': 'string'}}")
public class OrderEntity extends BaseEntity {

    @Indexed(unique = true)
    private String orderCode;
    private String customerId;
    private String assignedEmployeeId;
    private String assignedByAccountId;
    private Instant assignedAt;
    private String status;
    private String paymentStatus;
    private PaymentEntity.Method paymentMethod;
    private String currency = "VND";
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal subtotalAmount;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal shippingFee;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal promotionDiscount;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalAmount;
    private long pointsEarned;
    private Instant pointsProcessedAt;
    private AddressSnapshot shippingAddress;
    private String note;
    @Indexed
    private Instant orderedAt;
    private Instant paymentDueAt;
    private Instant stockReservedUntil;
    private Instant stockIssuedAt;
    private String stockIssuedByAccountId;
    private Instant confirmedAt;
    private Instant packingStartedAt;
    private Instant shippedAt;
    private Instant deliveredAt;
    private String carrierName;
    private String trackingCode;
    private String lastStatusChangedByAccountId;
    private Instant completedAt;
    private Instant cancelledAt;
    private String cancellationReason;
    private String checkoutKey;
    private String checkoutFingerprint;

    @Version
    private Long version;

    public record AddressSnapshot(String receiverName, String receiverPhone, String province,
                                  String district, String ward, String streetAddress) {
    }
}
