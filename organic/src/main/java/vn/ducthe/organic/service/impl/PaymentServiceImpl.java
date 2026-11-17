package vn.ducthe.organic.service.impl;

import static vn.ducthe.organic.mapper.PaymentMapper.event;
import static vn.ducthe.organic.mapper.PaymentMapper.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.dto.response.PaymentResponses.PaymentView;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.PaymentEntity;
import vn.ducthe.organic.entity.PaymentEntity.Method;
import vn.ducthe.organic.entity.PaymentEntity.Status;
import vn.ducthe.organic.entity.PaymentEventEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.integration.payment.PaymentGateways;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.repository.OrderTransactions;
import vn.ducthe.organic.service.PaymentService;
import vn.ducthe.organic.service.impl.OrderStaffAccess;

@Service
public class PaymentServiceImpl implements PaymentService {
    private final MongoDocumentRepository mongo;
    private final AuthStore auth;
    private final OrderTransactions transactions;
    private final PaymentGateways gateways;
    private final OrderStaffAccess orderStaff;

    public PaymentServiceImpl(MongoDocumentRepository mongo, AuthStore auth, OrderTransactions transactions,
                   PaymentGateways gateways, OrderStaffAccess orderStaff) {
        this.mongo = mongo;
        this.auth = auth;
        this.transactions = transactions;
        this.gateways = gateways;
        this.orderStaff = orderStaff;
    }

    @Override
    public void requireConfigured(Method method) {
        gateways.requireConfigured(method);
    }

    @Override
    public void requireOnlineAmount(Method method, BigDecimal amount) {
        // Gateways use whole VND. VNPAY's amount is at most 12 digits after multiplying by 100.
        BigDecimal max = method == Method.VNPAY ? BigDecimal.valueOf(9_999_999_999L)
                : BigDecimal.valueOf(Integer.MAX_VALUE);
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0 || amount.compareTo(max) > 0) {
            throw new ApiException(ApiError.INVALID_PAYMENT_AMOUNT);
        }
    }

    @Override
    public PaymentView get(String accountId, String orderCode) {
        return view(byOrder(owned(accountId, orderCode).getId()));
    }

    @Override
    public PaymentView start(String accountId, String orderCode, String ipAddress) {
        OrderEntity order = owned(accountId, orderCode);
        PaymentEntity payment = byOrder(order.getId());
        if (payment.getMethod() == Method.COD || payment.getStatus() == Status.PAID) {
            return view(payment);
        }
        Instant now = Instant.now();
        if (!"PENDING".equals(order.getStatus()) || payment.getStatus() != Status.PENDING
                || payment.getExpiresAt() == null || !now.isBefore(payment.getExpiresAt())) {
            throw new ApiException(ApiError.PAYMENT_NOT_PAYABLE);
        }
        if (payment.getPaymentUrl() != null) {
            return view(payment);
        }
        PaymentGateways.Link link = switch (payment.getMethod()) {
            case VNPAY -> gateways.vnpayLink(payment, orderCode, ipAddress);
            case VIETQR -> gateways.payosLink(payment, orderCode);
            case COD -> throw new IllegalStateException("COD does not use a payment link");
        };
        Query pending = Query.query(Criteria.where("_id").is(payment.getId()).and("status").is(Status.PENDING)
                .and("paymentUrl").is(null).and("expiresAt").gt(Instant.now()));
        mongo.updateFirst(pending, new Update().set("paymentUrl", link.url()).set("qrPayload", link.qr())
                .set("providerLinkId", link.providerLinkId()).set("updatedAt", Instant.now()), PaymentEntity.class);
        PaymentEntity current = byOrder(order.getId());
        if (current.getStatus() != Status.PAID && (current.getStatus() != Status.PENDING
                || current.getPaymentUrl() == null || !Instant.now().isBefore(current.getExpiresAt()))) {
            throw new ApiException(ApiError.PAYMENT_NOT_PAYABLE);
        }
        return view(current);
    }

    @Override
    public PaymentView confirmCod(String accountId, String orderCode) {
        return transactions.execute(() -> {
            var staff = orderStaff.require(accountId);
            OrderEntity order = mongo.findOne(query("orderCode", orderCode), OrderEntity.class);
            if (order == null) {
                throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
            }
            staff.requireAssigned(order);
            PaymentEntity payment = byOrder(order.getId());
            if (payment.getMethod() != Method.COD) {
                throw new ApiException(ApiError.PAYMENT_NOT_PAYABLE);
            }
            if (payment.getStatus() == Status.PAID) {
                return view(payment);
            }
            if (payment.getStatus() != Status.PENDING || !"DELIVERED".equals(order.getStatus())
                    || !"PENDING".equals(order.getPaymentStatus())
                    || payment.getAmount().compareTo(order.getTotalAmount()) != 0) {
                throw new ApiException(ApiError.PAYMENT_NOT_PAYABLE);
            }
            Instant now = Instant.now();
            payment.setConfirmedByEmployeeId(staff.employeeId());
            markPaid(order, payment, now);
            var event = event(payment, "COD", payment.getPaymentCode(), "PAYMENT_PAID", null, payment.getAmount(), now);
            event.setRecordedByAccountId(accountId);
            event.setProcessingStatus("APPLIED");
            mongo.insert(event);
            return view(payment);
        });
    }

    @Override
    public String verifyVnpayReturn(String accountId, String orderCode, Map<String, String> fields) {
        OrderEntity order = owned(accountId, orderCode);
        PaymentEntity payment = byOrder(order.getId());
        if (!gateways.validVnpay(fields)) {
            throw new ApiException(ApiError.INVALID_PAYMENT_SIGNATURE);
        }
        String response = fields.get("vnp_ResponseCode");
        String status = fields.get("vnp_TransactionStatus");
        if (payment.getMethod() != Method.VNPAY || !payment.getMerchantReference().equals(fields.get("vnp_TxnRef"))
                || response == null || status == null) {
            throw new ApiException(ApiError.INVALID_PAYMENT_NOTIFICATION);
        }
        BigDecimal reportedAmount = amount(fields.get("vnp_Amount"));
        if (reportedAmount == null || payment.getAmount().compareTo(reportedAmount.movePointLeft(2)) != 0
                || order.getTotalAmount().compareTo(payment.getAmount()) != 0) {
            throw new ApiException(ApiError.INVALID_PAYMENT_AMOUNT);
        }
        if ("00".equals(response) && "00".equals(status)) {
            String transaction = fields.get("vnp_TransactionNo");
            if (!validReference(transaction) || "0".equals(transaction)) {
                throw new ApiException(ApiError.INVALID_PAYMENT_NOTIFICATION);
            }
            return "PAID";
        }
        if ("07".equals(response) || "07".equals(status) || "04".equals(status) || "01".equals(status)) {
            return "PENDING";
        }
        return "FAILED";
    }

    @Override
    public CallbackResult vnpay(Map<String, String> fields) {
        if (!gateways.validVnpay(fields)) {
            return CallbackResult.INVALID_SIGNATURE;
        }
        String reference = fields.get("vnp_TxnRef");
        String transaction = fields.get("vnp_TransactionNo");
        String response = fields.get("vnp_ResponseCode");
        String status = fields.get("vnp_TransactionStatus");
        if (!validReference(reference) || response == null || status == null) {
            return CallbackResult.INVALID_DATA;
        }
        BigDecimal amount = amount(fields.get("vnp_Amount"));
        if (amount == null) {
            return CallbackResult.INVALID_AMOUNT;
        }
        boolean paid = "00".equals(response) && "00".equals(status);
        if (paid && (!validReference(transaction) || "0".equals(transaction))) {
            return CallbackResult.INVALID_DATA;
        }
        boolean review = "07".equals(response) || "07".equals(status) || "04".equals(status);
        Notice notice = new Notice(Method.VNPAY, "VNPAY", reference, null, transaction,
                fields.get("vnp_SecureHash").toLowerCase(java.util.Locale.ROOT), amount.movePointLeft(2),
                paid ? Outcome.PAID : review ? Outcome.REVIEW : "01".equals(status) ? Outcome.PENDING : Outcome.FAILED,
                response);
        return apply(notice);
    }

    @Override
    public CallbackResult payos(Map<String, Object> payload) {
        if (!(payload.get("data") instanceof Map<?, ?> data)
                || !gateways.validPayos(data, PaymentGateways.string(payload, "signature"))) {
            return CallbackResult.INVALID_SIGNATURE;
        }
        String reference = PaymentGateways.string(data, "orderCode");
        String transaction = PaymentGateways.string(data, "reference");
        String link = PaymentGateways.string(data, "paymentLinkId");
        String status = PaymentGateways.string(data, "code");
        if (!validReference(reference) || !validReference(transaction) || !validReference(link)
                || status == null || !"VND".equals(PaymentGateways.string(data, "currency"))) {
            return CallbackResult.INVALID_DATA;
        }
        BigDecimal amount = amount(PaymentGateways.string(data, "amount"));
        if (amount == null) {
            return CallbackResult.INVALID_AMOUNT;
        }
        return apply(new Notice(Method.VIETQR, "PAYOS", reference, link, transaction, link + ":" + transaction,
                amount, "00".equals(status) ? Outcome.PAID : Outcome.REVIEW, status));
    }

    private CallbackResult apply(Notice notice) {
        try {
            return transactions.execute(() -> applyInTransaction(notice));
        } catch (DuplicateKeyException exception) {
            if (findEvent(notice) != null) {
                return CallbackResult.ALREADY_CONFIRMED;
            }
            throw exception;
        }
    }

    private CallbackResult applyInTransaction(Notice notice) {
        PaymentEntity payment = mongo.findOne(Query.query(Criteria.where("method").is(notice.method())
                .and("merchantReference").is(notice.reference())), PaymentEntity.class);
        if (payment == null) {
            return CallbackResult.NOT_FOUND;
        }
        OrderEntity order = mongo.findById(payment.getOrderId(), OrderEntity.class);
        if (order == null || order.getPaymentMethod() != notice.method()) {
            return CallbackResult.NOT_FOUND;
        }
        if (payment.getProviderLinkId() != null && !payment.getProviderLinkId().equals(notice.linkId())) {
            return CallbackResult.INVALID_DATA;
        }
        PaymentEventEntity previous = findEvent(notice);
        if (previous != null) {
            return "AMOUNT_MISMATCH".equals(previous.getErrorMessage())
                    ? CallbackResult.INVALID_AMOUNT : CallbackResult.ALREADY_CONFIRMED;
        }

        Instant now = Instant.now();
        var event = event(payment, notice.source(), notice.eventKey(), "PAYMENT_" + notice.outcome().name(),
                notice.transaction(), notice.amount(), now);
        if (payment.getAmount().compareTo(notice.amount()) != 0 || order.getTotalAmount().compareTo(notice.amount()) != 0) {
            event.setProcessingStatus("RECONCILIATION_REQUIRED");
            event.setErrorMessage("AMOUNT_MISMATCH");
            mongo.insert(event);
            return CallbackResult.INVALID_AMOUNT;
        }
        if (notice.outcome() == Outcome.REVIEW) {
            event.setProcessingStatus("RECONCILIATION_REQUIRED");
            event.setErrorMessage("PROVIDER_REVIEW");
        } else if (notice.outcome() == Outcome.PAID) {
            applyPaid(order, payment, notice, event, now);
        } else if (notice.outcome() == Outcome.FAILED && payment.getStatus() == Status.PENDING
                && "PENDING".equals(order.getStatus())) {
            payment.setStatus(Status.FAILED);
            payment.setFailureCode(notice.responseCode());
            payment.setUpdatedAt(now);
            order.setPaymentStatus(Status.FAILED.name());
            order.setUpdatedAt(now);
            mongo.save(payment);
            mongo.save(order);
            event.setProcessingStatus("APPLIED");
        } else {
            event.setProcessingStatus("IGNORED");
        }
        mongo.insert(event);
        return CallbackResult.ACCEPTED;
    }

    private void applyPaid(OrderEntity order, PaymentEntity payment, Notice notice, PaymentEventEntity event, Instant now) {
        if (payment.getStatus() == Status.PAID && Objects.equals(payment.getGatewayTransactionId(), notice.transaction())) {
            event.setProcessingStatus("IGNORED");
            return;
        }
        boolean payable = "PENDING".equals(order.getStatus())
                && (payment.getStatus() == Status.PENDING || payment.getStatus() == Status.FAILED)
                && (Status.PENDING.name().equals(order.getPaymentStatus()) || Status.FAILED.name().equals(order.getPaymentStatus()));
        Query reference = Query.query(Criteria.where("method").is(notice.method())
                .and("gatewayTransactionId").is(notice.transaction()).and("_id").ne(payment.getId()));
        if (!payable || mongo.exists(reference, PaymentEntity.class)) {
            event.setProcessingStatus("RECONCILIATION_REQUIRED");
            event.setErrorMessage("PAYMENT_STATE_OR_TRANSACTION_CONFLICT");
            return;
        }
        payment.setGatewayTransactionId(notice.transaction());
        payment.setProviderLinkId(notice.linkId());
        payment.setFailureCode(null);
        markPaid(order, payment, now);
        event.setProcessingStatus("APPLIED");
    }

    private void markPaid(OrderEntity order, PaymentEntity payment, Instant now) {
        payment.setStatus(Status.PAID);
        payment.setPaidAt(now);
        payment.setUpdatedAt(now);
        order.setPaymentStatus(Status.PAID.name());
        order.setUpdatedAt(now);
        mongo.save(payment);
        mongo.save(order);
    }

    private PaymentEventEntity findEvent(Notice notice) {
        return mongo.findOne(Query.query(Criteria.where("source").is(notice.source())
                .and("eventKey").is(notice.eventKey())), PaymentEventEntity.class);
    }

    private OrderEntity owned(String accountId, String orderCode) {
        String customerId = auth.customer(accountId).getId();
        OrderEntity order = mongo.findOne(Query.query(Criteria.where("customerId").is(customerId)
                .and("orderCode").is(orderCode)), OrderEntity.class);
        if (order == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return order;
    }

    private PaymentEntity byOrder(String orderId) {
        PaymentEntity payment = mongo.findOne(query("orderId", orderId), PaymentEntity.class);
        if (payment == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return payment;
    }

    private static Query query(String field, Object value) {
        return Query.query(Criteria.where(field).is(value));
    }

    private static boolean validReference(String value) {
        return value != null && value.matches("[a-zA-Z0-9_-]{1,128}");
    }

    private static BigDecimal amount(String value) {
        return value != null && value.matches("[0-9]{1,16}") ? new BigDecimal(value) : null;
    }

    private enum Outcome { PAID, FAILED, PENDING, REVIEW }

    private record Notice(Method method, String source, String reference, String linkId, String transaction,
                          String eventKey, BigDecimal amount, Outcome outcome, String responseCode) {}
}
