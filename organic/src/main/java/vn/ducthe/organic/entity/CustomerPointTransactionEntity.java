package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "customer_point_transactions")
@CompoundIndex(name = "customerId_idempotencyKey", def = "{'customerId': 1, 'idempotencyKey': 1}", unique = true)
@CompoundIndex(name = "customerId_occurredAt", def = "{'customerId': 1, 'occurredAt': 1}")
@CompoundIndex(name = "earn_points_once_per_order", def = "{'orderId': 1}", unique = true,
        partialFilter = "{'type': 'EARN', 'orderId': {'$type': 'string'}}")
public class CustomerPointTransactionEntity extends BaseEntity {

    private String customerId;
    private String orderId;
    private String customerVoucherId;
    private String idempotencyKey;
    private Type type;
    private long pointsDelta;
    private long balanceAfter;
    private String reason;
    private String recordedByAccountId;
    private Instant occurredAt;

    public enum Type {
        EARN, REDEEM, ADJUSTMENT
    }
}
