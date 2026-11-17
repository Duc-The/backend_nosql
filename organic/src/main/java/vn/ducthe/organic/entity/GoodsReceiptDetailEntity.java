package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Document(collection = "goods_receipt_details")
public class GoodsReceiptDetailEntity extends BaseEntity {

    @Indexed
    private String goodsReceiptId;
    private String variantId;
    private String sku;
    private String variantName;
    @Indexed
    private String batchId;
    private Integer lineNumber;
    private String lotCode;
    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    private String originDescription;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal quantityReceived;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal quantityRejected;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal unitCost;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal lineTotal;
    private String discrepancyReason;
}
