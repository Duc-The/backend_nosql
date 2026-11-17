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
@Document(collection = "promotion_products")
@CompoundIndex(name = "promotionId_productId", def = "{'promotionId': 1, 'productId': 1}", unique = true)
public class PromotionProductEntity extends BaseEntity {

    private String promotionId;
    @Indexed
    private String productId;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal minimumQuantity;
}
