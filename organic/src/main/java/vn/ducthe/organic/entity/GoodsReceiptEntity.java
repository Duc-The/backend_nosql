package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Document(collection = "goods_receipts")
@CompoundIndex(name = "supplierId_receivedAt", def = "{'supplierId': 1, 'receivedAt': 1}")
@CompoundIndex(name = "status_receivedAt", def = "{'status': 1, 'receivedAt': -1}")
public class GoodsReceiptEntity extends BaseEntity {

    @Indexed(unique = true)
    private String receiptCode;
    private String supplierId;
    private String receivedByEmployeeId;
    private String confirmedByEmployeeId;
    private Instant receivedAt;
    private Instant confirmedAt;
    private String status;
    private String currency = "VND";
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalCost;
    private String note;
    private String confirmationNote;
    private String cancelledByEmployeeId;
    private Instant cancelledAt;
    private String cancellationReason;
}
