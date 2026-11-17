package vn.ducthe.organic.common.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.query.Criteria;
import vn.ducthe.organic.entity.ProductBatchEntity;
import vn.ducthe.organic.repository.AuthStore;

public final class BatchAvailability {
    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Decimal128 ZERO = Decimal128.POSITIVE_ZERO;

    private BatchAvailability() {}

    public static LocalDate shopDate(Instant time) {
        return time.atZone(SHOP_ZONE).toLocalDate();
    }

    public static Criteria sellableBatches(Collection<String> variantIds, LocalDate today) {
        return Criteria.where("variantId").in(variantIds).and("status").is(AuthStore.ACTIVE)
                .and("expiryDate").gte(today);
    }

    public static boolean isSellable(ProductBatchEntity batch, LocalDate today) {
        return AuthStore.ACTIVE.equals(batch.getStatus()) && batch.getExpiryDate() != null
                && !batch.getExpiryDate().isBefore(today);
    }

    public static BigDecimal unreserved(ProductBatchEntity batch) {
        return amount(batch.getQuantityOnHand()).subtract(amount(batch.getQuantityReserved())).max(BigDecimal.ZERO);
    }

    public static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static Document unreservedExpression() {
        Document balance = new Document("$subtract", List.of(nonNull("$quantityOnHand"), nonNull("$quantityReserved")));
        return new Document("$max", List.of(ZERO, balance));
    }

    public static Document nonNull(String field) {
        return new Document("$ifNull", List.of(field, ZERO));
    }
}
