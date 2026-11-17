package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "customer_vouchers")
@CompoundIndex(name = "customerId_promotionId_status", def = "{'customerId': 1, 'promotionId': 1, 'status': 1}")
public class CustomerVoucherEntity extends BaseEntity {

    private String customerId;
    private String promotionId;
    @Indexed(unique = true)
    private String voucherCode;
    private String status;
    private Integer usageLimit;
    private long usageCount;
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant lastUsedAt;

    @Version
    private Long version;
}
