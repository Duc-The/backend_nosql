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

@Getter
@Setter
@Document(collection = "promotions")
@CompoundIndex(name = "status_startsAt_endsAt", def = "{'status': 1, 'startsAt': 1, 'endsAt': 1}")
public class PromotionEntity extends BaseEntity {

    @Indexed(unique = true)
    private String promotionCode;
    private String name;
    private String description;
    private Audience audience;
    private Target target;
    private DiscountType discountType;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal discountValue;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal maxDiscountAmount;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal minOrderValue;
    private Instant startsAt;
    private Instant endsAt;
    private Integer usageLimit;
    private long usageCount;
    private Integer perCustomerLimit;
    private String status;

    @Version
    private Long version;

    public enum Audience {
        ALL_CUSTOMERS, ASSIGNED_CUSTOMERS
    }

    public enum Target {
        ORDER, PRODUCT, BATCH
    }

    public enum DiscountType {
        PERCENTAGE, FIXED_AMOUNT
    }
}
