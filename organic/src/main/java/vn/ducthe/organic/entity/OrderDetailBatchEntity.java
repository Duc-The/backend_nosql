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
@Document(collection = "order_detail_batches")
@CompoundIndex(name = "orderDetailId_batchId", def = "{'orderDetailId': 1, 'batchId': 1}", unique = true)
@CompoundIndex(name = "batchId_status", def = "{'batchId': 1, 'status': 1}")
public class OrderDetailBatchEntity extends BaseEntity {

    private String orderDetailId;
    private String batchId;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal quantity;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal releasedQuantity;
    private String status;
    private Instant reservedAt;
    private Instant issuedAt;
    private Instant releasedAt;
}
