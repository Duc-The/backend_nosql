package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

@Getter
@Setter
@Document(collection = "order_details")
public class OrderDetailEntity extends BaseEntity {

    @Indexed
    private String orderId;
    private String variantId;
    private String productId;
    private String productName;
    private String variantName;
    private String sku;
    private String unitCode;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal quantity;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal unitPrice;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal discountAmount;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal vatRate;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal lineTotal;
}
