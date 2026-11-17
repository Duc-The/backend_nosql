package vn.ducthe.organic.service.impl;

import static vn.ducthe.organic.mapper.OrderMapper.initialize;
import static vn.ducthe.organic.mapper.OrderMapper.line;
import static vn.ducthe.organic.mapper.OrderMapper.tracking;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.inventory.StockHoldPolicy;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.OrderResponses.Detail;
import vn.ducthe.organic.dto.response.OrderResponses.Line;
import vn.ducthe.organic.dto.response.OrderResponses.Summary;
import vn.ducthe.organic.dto.response.OrderResponses.WarehouseOrder;
import vn.ducthe.organic.dto.response.OrderResponses.WarehouseQueueItem;
import vn.ducthe.organic.entity.OrderDetailEntity;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.PaymentEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.repository.OrderTransactions;
import vn.ducthe.organic.service.CartService;
import vn.ducthe.organic.service.OrderService;
import vn.ducthe.organic.service.PaymentService;
import vn.ducthe.organic.service.PromotionService;
import vn.ducthe.organic.service.impl.OrderInventory;

@Service
public class OrderServiceImpl implements OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String PENDING = "PENDING";
    private static final String CANCELLED = "CANCELLED";
    private static final String PACKED = "PACKED";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MongoDocumentRepository mongo;
    private final CartService cart;
    private final AuthStore auth;
    private final OrderTransactions transactions;
    private final PaymentService payments;
    private final PromotionService promotions;
    private final Duration cancellationWindow;
    private final Duration onlinePaymentWindow;
    private final StockHoldPolicy holds;
    private final OrderInventory inventory;

    public OrderServiceImpl(MongoDocumentRepository mongo, CartService cart, AuthStore auth, OrderTransactions transactions,
                        PaymentService payments, PromotionService promotions, StockHoldPolicy holds, OrderInventory inventory,
                        @Value("${app.orders.cancellation-window:PT30M}") Duration cancellationWindow,
                        @Value("${app.payment.online-window:PT15M}") Duration onlinePaymentWindow) {
        this.mongo = mongo;
        this.cart = cart;
        this.auth = auth;
        this.transactions = transactions;
        this.payments = payments;
        this.promotions = promotions;
        this.cancellationWindow = cancellationWindow;
        this.onlinePaymentWindow = onlinePaymentWindow;
        this.holds = holds;
        this.inventory = inventory;
        if (cancellationWindow.isNegative() || cancellationWindow.isZero()) {
            throw new IllegalArgumentException("app.orders.cancellation-window must be positive");
        }
        if (onlinePaymentWindow.isNegative() || onlinePaymentWindow.isZero()) {
            throw new IllegalArgumentException("app.payment.online-window must be positive");
        }
    }

    @Override
    public CheckoutResult checkout(String accountId, String key, String note, PaymentEntity.Method requestedMethod) {
        String customerId = auth.customer(accountId).getId();
        PaymentEntity.Method method = requestedMethod == null ? PaymentEntity.Method.COD : requestedMethod;
        String normalizedNote = note == null || note.isBlank() ? null : note.strip();
        String content = Objects.toString(normalizedNote, "");
        // Preserve fingerprints for COD orders created before online methods were introduced.
        String fingerprint = UUID.nameUUIDFromBytes((method == PaymentEntity.Method.COD ? content
                : method.name() + ":" + content)
                .getBytes(StandardCharsets.UTF_8)).toString();

        OrderEntity existing = byKey(customerId, key);
        if (existing != null) {
            return replay(existing, fingerprint, method);
        }
        payments.requireConfigured(method);

        Created created;
        try {
            created = transactions.execute(() -> create(customerId, key, normalizedNote, fingerprint, method));
        } catch (DuplicateKeyException exception) {
            OrderEntity committed = byKey(customerId, key);
            if (committed != null) {
                return replay(committed, fingerprint, method);
            }
            throw exception;
        } catch (ApiException exception) {
            if (exception.error() == ApiError.ORDER_RETRY) {
                OrderEntity committed = byKey(customerId, key);
                if (committed != null) {
                    return replay(committed, fingerprint, method);
                }
            }
            throw exception;
        }

        if (created.fresh()) {
            try {
                cart.clearAfterCheckout(customerId, created.cartRevision());
            } catch (RuntimeException exception) {
                log.warn("Could not clear Redis cart after order {}", created.order().getOrderCode(), exception);
            }
        }
        return new CheckoutResult(detail(created.order()), created.fresh());
    }

    @Override
    public PageData<Summary> list(String accountId, int page, int size) {
        String customerId = auth.customer(accountId).getId();
        var pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "orderedAt", "_id"));
        Query filter = query("customerId", customerId);
        long total = mongo.count(filter, OrderEntity.class);
        List<Summary> items = mongo.find(Query.of(filter).with(pageable), OrderEntity.class).stream()
                .map(this::summary).toList();
        return PageData.from(new PageImpl<>(items, pageable, total));
    }

    @Override
    public Detail detail(String accountId, String orderCode) {
        return detail(owned(auth.customer(accountId).getId(), orderCode));
    }

    @Override
    public Detail cancel(String accountId, String orderCode, String reason) {
        String customerId = auth.customer(accountId).getId();
        OrderEntity result = transactions.execute(() -> cancelPending(customerId, accountId, orderCode, reason.strip()));
        return detail(result);
    }

    @Scheduled(fixedDelayString = "${app.payment.expiry-scan-delay:PT1M}", initialDelayString = "PT1M")
    @Override
    public void expireHolds() {
        Instant now = Instant.now();
        expireHolds(true, now, "Đơn đã hết hạn giữ hàng");
        expireHolds(false, now, "Đơn đã hết hạn thanh toán");
    }

    private void expireHolds(boolean cod, Instant now, String reason) {
        Query query = Query.query(holds.expired(cod, now)).with(Sort.by("stockReservedUntil", "_id")).limit(50);
        query.fields().include("_id");
        for (OrderEntity candidate : mongo.find(query, OrderEntity.class)) {
            try {
                transactions.execute(() -> {
                    Query due = Query.query(holds.expired(cod, now)).addCriteria(Criteria.where("_id").is(candidate.getId()));
                    OrderEntity order = mongo.findOne(due, OrderEntity.class);
                    if (order != null) {
                        releaseOrder(order, Instant.now(), reason, PaymentEntity.Status.EXPIRED, null);
                    }
                    return null;
                });
            } catch (RuntimeException exception) {
                log.warn("Could not release expired stock hold for order {}", candidate.getId(), exception);
            }
        }
    }

    @Override
    public PageData<WarehouseQueueItem> warehouseQueue(int page, int size) {
        var pageable = PageRequest.of(page - 1, size, Sort.by("orderedAt", "_id"));
        Query filter = Query.query(holds.warehouseQueue(Instant.now()));
        long total = mongo.count(filter, OrderEntity.class);
        List<WarehouseQueueItem> rows = mongo.find(Query.of(filter).with(pageable), OrderEntity.class).stream()
                .map(this::warehouseQueueItem).toList();
        return PageData.from(new PageImpl<>(rows, pageable, total));
    }

    @Override
    public WarehouseOrder warehouseOrder(String orderCode) {
        return warehouseOrder(findOrder(orderCode));
    }

    @Override
    public WarehouseOrder issue(String orderCode, String accountId) {
        OrderEntity result = transactions.execute(() -> {
            OrderEntity order = findOrder(orderCode);
            if (order.getStockIssuedAt() != null || PACKED.equals(order.getStatus())) {
                return order;
            }
            Instant now = Instant.now();
            boolean paid = PaymentEntity.Status.PAID.name().equals(order.getPaymentStatus());
            boolean codPending = order.getPaymentMethod() == PaymentEntity.Method.COD
                    && PENDING.equals(order.getPaymentStatus());
            if (!"PACKING".equals(order.getStatus()) || !paid && !codPending) {
                throw new ApiException(ApiError.ORDER_NOT_ISSUABLE);
            }
            if (!paid && holds.expiredBeforeIssue(order, now)) {
                throw new ApiException(ApiError.STOCK_HOLD_EXPIRED);
            }

            order.setStatus(PACKED);
            order.setStockIssuedAt(now);
            order.setStockIssuedByAccountId(accountId);
            order.setLastStatusChangedByAccountId(accountId);
            order.setUpdatedAt(now);
            OrderEntity updated = mongo.save(order);
            List<OrderInventory.PlannedBatch> required = updated.getPromotionDiscount() != null
                    && updated.getPromotionDiscount().signum() > 0 ? promotions.requiredBatches(updated.getId()) : List.of();
            inventory.issue(lines(updated), required, now);
            return updated;
        });
        return warehouseOrder(result);
    }

    private WarehouseQueueItem warehouseQueueItem(OrderEntity order) {
        Instant deadline = StockHoldPolicy.BEFORE_ISSUE.contains(order.getStatus())
                && !PaymentEntity.Status.PAID.name().equals(order.getPaymentStatus()) ? holds.deadline(order) : null;
        return new WarehouseQueueItem(order.getOrderCode(), order.getStatus(), order.getPaymentMethod(),
                order.getPaymentStatus(), order.getOrderedAt(), deadline);
    }

    private WarehouseOrder warehouseOrder(OrderEntity order) {
        return new WarehouseOrder(warehouseQueueItem(order), order.getStockIssuedAt(), order.getStockIssuedByAccountId(),
                inventory.view(lines(order)));
    }

    @Override
    public OrderEntity findOrder(String orderCode) {
        OrderEntity order = mongo.findOne(query("orderCode", orderCode), OrderEntity.class);
        if (order == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return order;
    }

    private Created create(String customerId, String key, String note, String fingerprint, PaymentEntity.Method method) {
        OrderEntity existing = byKey(customerId, key);
        if (existing != null) {
            requireSameRequest(existing, fingerprint, method);
            return new Created(existing, null, false);
        }
        Instant now = Instant.now();
        CartService.CheckoutCart checkout = cart.checkoutCart(customerId, now);
        if (checkout.cart().items().isEmpty()) {
            throw new ApiException(ApiError.EMPTY_CART);
        }
        if (checkout.shippingAddress() == null) {
            throw new ApiException(ApiError.DELIVERY_ADDRESS_REQUIRED);
        }
        if (checkout.promotionError() != null) {
            throw new ApiException(checkout.promotionError());
        }
        if (!checkout.cart().canCheckout()) {
            throw new ApiException(ApiError.INSUFFICIENT_STOCK);
        }
        if (method != PaymentEntity.Method.COD) {
            payments.requireOnlineAmount(method, checkout.cart().totalAmount());
        }

        OrderEntity order = new OrderEntity();
        initialize(order, now);
        order.setOrderCode("DH-" + order.getId());
        order.setCustomerId(customerId);
        order.setCheckoutKey(key);
        order.setCheckoutFingerprint(fingerprint);
        order.setStatus(PENDING);
        order.setPaymentMethod(method);
        order.setPaymentStatus(PENDING);
        if (method != PaymentEntity.Method.COD) {
            order.setPaymentDueAt(now.plus(onlinePaymentWindow));
        }
        order.setShippingAddress(checkout.shippingAddress());
        order.setSubtotalAmount(checkout.cart().subtotalAmount());
        order.setShippingFee(checkout.cart().shippingFee());
        order.setPromotionDiscount(checkout.cart().promotionDiscount());
        order.setTotalAmount(checkout.cart().totalAmount());
        order.setNote(note);
        order.setOrderedAt(now);
        order.setStockReservedUntil(holds.deadline(order));
        mongo.insert(order);

        List<OrderDetailEntity> orderLines = checkout.cart().items().stream().map(item -> line(order.getId(), item, now)).toList();
        mongo.insert(orderLines, OrderDetailEntity.class);
        List<OrderInventory.PlannedBatch> required = checkout.promotion() == null ? List.of()
                : promotions.claim(order, orderLines, checkout.promotion(), now);
        inventory.reserve(orderLines, required, now);

        PaymentEntity payment = new PaymentEntity();
        initialize(payment, now);
        payment.setPaymentCode("TT-" + payment.getId());
        payment.setOrderId(order.getId());
        payment.setMethod(method);
        payment.setStatus(PaymentEntity.Status.PENDING);
        payment.setAmount(order.getTotalAmount());
        payment.setExpiresAt(order.getPaymentDueAt());
        if (method == PaymentEntity.Method.VNPAY) {
            payment.setMerchantReference(payment.getId().replace("-", ""));
        } else if (method == PaymentEntity.Method.VIETQR) {
            payment.setMerchantReference(Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L)));
        }
        mongo.insert(payment);
        return new Created(order, checkout.revision(), true);
    }

    private OrderEntity cancelPending(String customerId, String accountId, String orderCode, String reason) {
        OrderEntity order = owned(customerId, orderCode);
        if (CANCELLED.equals(order.getStatus())) {
            return order;
        }
        Instant now = Instant.now();
        if (!canCancel(order, now)) {
            throw new ApiException(ApiError.ORDER_NOT_CANCELLABLE);
        }
        return releaseOrder(order, now, reason, PaymentEntity.Status.CANCELLED, accountId);
    }

    private OrderEntity releaseOrder(OrderEntity order, Instant now, String reason,
                                     PaymentEntity.Status paymentStatus, String actor) {
        promotions.release(order.getId(), now);
        inventory.release(lines(order), now);
        mongo.updateMulti(Query.query(Criteria.where("orderId").is(order.getId()).and("status")
                        .in(PaymentEntity.Status.PENDING, PaymentEntity.Status.FAILED)),
                new Update().set("status", paymentStatus)
                        .set("updatedAt", now), PaymentEntity.class);

        order.setStatus(CANCELLED);
        order.setPaymentStatus(paymentStatus.name());
        order.setCancelledAt(now);
        order.setCancellationReason(reason);
        order.setLastStatusChangedByAccountId(actor);
        order.setUpdatedAt(now);
        return mongo.save(order);
    }

    private OrderEntity owned(String customerId, String orderCode) {
        OrderEntity order = mongo.findOne(Query.query(Criteria.where("customerId").is(customerId)
                .and("orderCode").is(orderCode)), OrderEntity.class);
        if (order == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return order;
    }

    private OrderEntity byKey(String customerId, String key) {
        return mongo.findOne(Query.query(Criteria.where("customerId").is(customerId)
                .and("checkoutKey").is(key)), OrderEntity.class);
    }

    private CheckoutResult replay(OrderEntity order, String fingerprint, PaymentEntity.Method method) {
        requireSameRequest(order, fingerprint, method);
        return new CheckoutResult(detail(order), false);
    }

    private static void requireSameRequest(OrderEntity order, String fingerprint, PaymentEntity.Method method) {
        if (order.getPaymentMethod() != method || !Objects.equals(order.getCheckoutFingerprint(), fingerprint)) {
            throw new ApiException(ApiError.CHECKOUT_KEY_REUSED);
        }
    }

    private boolean canCancel(OrderEntity order, Instant now) {
        return PENDING.equals(order.getStatus())
                && (PENDING.equals(order.getPaymentStatus()) || "FAILED".equals(order.getPaymentStatus()))
                && now.isBefore(order.getOrderedAt().plus(cancellationWindow))
                && !holds.expiredBeforeIssue(order, now);
    }

    private List<OrderDetailEntity> lines(OrderEntity order) {
        return mongo.find(query("orderId", order.getId()).with(Sort.by("sku", "_id")), OrderDetailEntity.class);
    }

    private Summary summary(OrderEntity order) {
        return new Summary(order.getOrderCode(), order.getStatus(), order.getPaymentStatus(),
                order.getPaymentMethod(), order.getCurrency(), order.getTotalAmount(), order.getOrderedAt(),
                order.getPaymentDueAt(), StockHoldPolicy.BEFORE_ISSUE.contains(order.getStatus())
                        && !PaymentEntity.Status.PAID.name().equals(order.getPaymentStatus()) ? holds.deadline(order) : null,
                cancelUntil(order), canCancel(order, Instant.now()));
    }

    private Instant cancelUntil(OrderEntity order) {
        Instant end = order.getOrderedAt().plus(cancellationWindow);
        Instant hold = holds.deadline(order);
        return hold != null && hold.isBefore(end) ? hold : end;
    }

    @Override
    public Detail detail(OrderEntity order) {
        List<Line> items = lines(order).stream().map(line -> new Line(line.getProductId(), line.getVariantId(),
                line.getProductName(), line.getVariantName(), line.getSku(), line.getUnitCode(),
                line.getQuantity(), line.getUnitPrice(), line.getLineTotal())).toList();
        return new Detail(summary(order), order.getShippingAddress(), order.getSubtotalAmount(), order.getShippingFee(),
                order.getPromotionDiscount(), order.getPromotionDiscount() != null && order.getPromotionDiscount().signum() > 0
                        ? promotions.appliedCode(order.getId()) : null,
                order.getPointsEarned(), order.getNote(), tracking(order), order.getCompletedAt(), order.getCancelledAt(),
                order.getCancellationReason(), items);
    }

    private static Query query(String field, Object value) {
        return Query.query(Criteria.where(field).is(value));
    }

    private record Created(OrderEntity order, String cartRevision, boolean fresh) {}
}
