package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Document(collection = "order_promotions")
@CompoundIndex(name = "customer_promotion_redemptions", def = "{'customerId': 1, 'promotionId': 1, 'releasedAt': 1}")
public class OrderPromotionEntity extends BaseEntity {

    @Indexed(unique = true)
    private String orderId;
    private String customerId;
    @Indexed
    private String promotionId;
    private String customerVoucherId;
    private String promotionCode;
    private String promotionName;
    private PromotionEntity.Target target;
    private PromotionEntity.DiscountType discountType;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal discountValue;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal eligibleAmount;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal discountAmount;
    private List<AppliedLine> appliedLines = new ArrayList<>();
    private Instant appliedAt;
    private Instant releasedAt;

    public record AppliedLine(
            String orderDetailId,
            String batchId,
            @Field(targetType = FieldType.DECIMAL128) BigDecimal eligibleQuantity,
            @Field(targetType = FieldType.DECIMAL128) BigDecimal discountAmount) {
    }
}
