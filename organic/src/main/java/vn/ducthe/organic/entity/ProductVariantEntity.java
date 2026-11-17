package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

@Getter
@Setter
@Document(collection = "product_variants")
@CompoundIndex(name = "productId_status", def = "{'productId': 1, 'status': 1}")
public class ProductVariantEntity extends BaseEntity {

    private String productId;
    @Indexed(unique = true)
    private String sku;
    private String name;
    private String barcode;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal netQuantity;
    private String unitCode;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal sellingPrice;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal compareAtPrice;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal reorderLevel;
    private String status;
}
