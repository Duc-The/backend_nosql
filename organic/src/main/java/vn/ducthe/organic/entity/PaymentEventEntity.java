package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Document(collection = "payment_events")
@CompoundIndex(name = "source_eventKey", def = "{'source': 1, 'eventKey': 1}", unique = true)
@CompoundIndex(name = "paymentId_receivedAt", def = "{'paymentId': 1, 'receivedAt': 1}")
public class PaymentEventEntity extends BaseEntity {

    private String paymentId;
    private String source;
    private String eventKey;
    private String eventType;
    private String processingStatus;
    private String providerTransactionId;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal reportedAmount;
    private String currency;
    private Instant receivedAt;
    private Instant processedAt;
    private String recordedByAccountId;
    private String errorMessage;
}
