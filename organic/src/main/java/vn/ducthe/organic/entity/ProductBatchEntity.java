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
import java.time.LocalDate;

@Getter
@Setter
@Document(collection = "product_batches")
@CompoundIndex(name = "variantId_status_expiryDate", def = "{'variantId': 1, 'status': 1, 'expiryDate': 1}")
public class ProductBatchEntity extends BaseEntity {

    private String variantId;
    @Indexed(unique = true)
    private String batchCode;
    @Indexed
    private String lotCode;
    private LocalDate manufactureDate;
    @Indexed
    private LocalDate expiryDate;
    private String originDescription;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal quantityOnHand = BigDecimal.ZERO;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal quantityReserved = BigDecimal.ZERO;
    private String status;
    private Instant statusChangedAt;
    private String statusChangedByAccountId;
    private String statusReason;

    @Version
    private Long version;
}
