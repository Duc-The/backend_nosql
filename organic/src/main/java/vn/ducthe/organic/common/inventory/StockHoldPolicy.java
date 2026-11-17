package vn.ducthe.organic.common.inventory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.PaymentEntity;

@Component
public class StockHoldPolicy {
    public static final List<String> BEFORE_ISSUE = List.of("PENDING", "CONFIRMED", "PACKING");
    private final Duration codWindow;
    private final Duration callbackGrace;

    StockHoldPolicy(@Value("${app.inventory.cod-hold-window:PT2H}") Duration codWindow,
                    @Value("${app.payment.callback-grace:PT5M}") Duration callbackGrace) {
        this.codWindow = codWindow;
        this.callbackGrace = callbackGrace;
        if (codWindow.isNegative() || codWindow.isZero() || callbackGrace.isNegative()) {
            throw new IllegalArgumentException("Invalid stock hold configuration");
        }
    }

    public Instant deadline(OrderEntity order) {
        if (order.getStockReservedUntil() != null) {
            return order.getStockReservedUntil();
        }
        if (order.getPaymentMethod() == PaymentEntity.Method.COD && order.getOrderedAt() != null) {
            return order.getOrderedAt().plus(codWindow);
        }
        return order.getPaymentDueAt() == null ? null : order.getPaymentDueAt().plus(callbackGrace);
    }

    public boolean expiredBeforeIssue(OrderEntity order, Instant now) {
        Instant deadline = deadline(order);
        return BEFORE_ISSUE.contains(order.getStatus()) && deadline != null && !now.isBefore(deadline);
    }

    public Criteria expired(boolean cod, Instant now) {
        return new Criteria().andOperator(
                Criteria.where("status").in(BEFORE_ISSUE),
                Criteria.where("paymentStatus").in("PENDING", "FAILED"),
                cod ? Criteria.where("paymentMethod").is(PaymentEntity.Method.COD)
                        : Criteria.where("paymentMethod").in(PaymentEntity.Method.VIETQR, PaymentEntity.Method.VNPAY),
                elapsed(cod, now));
    }

    public Criteria warehouseQueue(Instant now) {
        return new Criteria().andOperator(Criteria.where("status").is("PACKING"),
                new Criteria().orOperator(
                        Criteria.where("paymentStatus").is("PAID"),
                        new Criteria().andOperator(Criteria.where("paymentMethod").is(PaymentEntity.Method.COD),
                                Criteria.where("paymentStatus").is("PENDING"),
                                new Criteria().norOperator(elapsed(true, now)))));
    }

    private Criteria elapsed(boolean cod, Instant now) {
        String legacyField = cod ? "orderedAt" : "paymentDueAt";
        Instant legacyCutoff = now.minus(cod ? codWindow : callbackGrace);
        return new Criteria().orOperator(Criteria.where("stockReservedUntil").lte(now),
                Criteria.where("stockReservedUntil").is(null).and(legacyField).lte(legacyCutoff));
    }
}
