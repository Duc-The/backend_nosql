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
@Document(collection = "promotion_batches")
@CompoundIndex(name = "promotionId_batchId", def = "{'promotionId': 1, 'batchId': 1}", unique = true)
public class PromotionBatchEntity extends BaseEntity {

    private String promotionId;
    @Indexed
    private String batchId;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal minimumQuantity;
}
