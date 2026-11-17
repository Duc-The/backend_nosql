package vn.ducthe.organic.service.impl;

import static vn.ducthe.organic.mapper.SalesOrderMapper.clean;
import static vn.ducthe.organic.mapper.SalesOrderMapper.row;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.inventory.StockHoldPolicy;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.SalesOrderResponses.Assignment;
import vn.ducthe.organic.dto.response.SalesOrderResponses.Detail;
import vn.ducthe.organic.dto.response.SalesOrderResponses.PaymentSummary;
import vn.ducthe.organic.dto.response.SalesOrderResponses.Row;
import vn.ducthe.organic.entity.EmployeeEntity;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.PaymentEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.repository.OrderTransactions;
import vn.ducthe.organic.service.OrderService;
import vn.ducthe.organic.service.SalesOrderService;
import vn.ducthe.organic.service.impl.OrderStaffAccess;
import vn.ducthe.organic.service.impl.OrderStaffAccess.Staff;

@Service
public class SalesOrderServiceImpl implements SalesOrderService {
    private final MongoDocumentRepository mongo;
    private final OrderService orders;
    private final OrderTransactions transactions;
    private final OrderStaffAccess access;
    private final StockHoldPolicy holds;

    public SalesOrderServiceImpl(MongoDocumentRepository mongo, OrderService orders, OrderTransactions transactions,
                      OrderStaffAccess access, StockHoldPolicy holds) {
        this.mongo = mongo;
        this.orders = orders;
        this.transactions = transactions;
        this.access = access;
        this.holds = holds;
    }

    @Override
    public PageData<Row> list(String accountId, Status status, String assignee, String q, int page, int size) {
        Staff staff = access.require(accountId);
        Query filter = new Query();
        if (!staff.admin()) {
            filter.addCriteria(new Criteria().orOperator(Criteria.where("assignedEmployeeId").is(staff.employeeId()),
                    Criteria.where("assignedEmployeeId").is(null).and("status").is(Status.PENDING.name())));
        }
        if (status != null) {
            filter.addCriteria(Criteria.where("status").is(status.name()));
        }
        if (assignee != null && !assignee.isBlank()) {
            filter.addCriteria(Criteria.where("assignedEmployeeId").is(assignee));
        }
        if (q != null && !q.isBlank()) {
            filter.addCriteria(Criteria.where("orderCode").regex(
                    Pattern.compile(Pattern.quote(q.strip()), Pattern.CASE_INSENSITIVE)));
        }
        var pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "orderedAt", "_id"));
        long total = mongo.count(filter, OrderEntity.class);
        List<OrderEntity> rows = mongo.find(Query.of(filter).with(pageable), OrderEntity.class);
        List<String> employeeIds = rows.stream().map(OrderEntity::getAssignedEmployeeId)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, EmployeeEntity> employees = employeeIds.isEmpty() ? Map.of()
                : mongo.find(Query.query(Criteria.where("_id").in(employeeIds)), EmployeeEntity.class).stream()
                        .collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        List<Row> result = rows.stream().map(order -> {
            String id = order.getAssignedEmployeeId();
            return row(order, id == null ? null : employees.get(id));
        }).toList();
        return PageData.from(new PageImpl<>(result, pageable, total));
    }

    @Override
    public Detail detail(String accountId, String orderCode) {
        Staff staff = access.require(accountId);
        OrderEntity order = orders.findOrder(orderCode);
        requireVisible(staff, order);
        return detail(order);
    }

    @Override
    public Detail assign(String accountId, String orderCode, String employeeId) {
        OrderEntity updated = transactions.execute(() -> {
            Staff staff = access.require(accountId);
            OrderEntity order = orders.findOrder(orderCode);
            requireVisible(staff, order);
            String target = employeeId == null || employeeId.isBlank() ? staff.employeeId() : employeeId.strip();
            if (target == null) {
                throw new ApiException(ApiError.ORDER_ASSIGNEE_REQUIRED);
            }
            if (!staff.admin() && !target.equals(staff.employeeId())) {
                throw new ApiException(ApiError.ACCESS_DENIED);
            }
            if (target.equals(order.getAssignedEmployeeId())) {
                return order;
            }
            if (Status.CANCELLED.name().equals(order.getStatus()) || Status.COMPLETED.name().equals(order.getStatus())) {
                throw new ApiException(ApiError.ORDER_STATUS_TRANSITION_INVALID);
            }
            EmployeeEntity employee = access.assignee(target);
            Instant now = Instant.now();
            assign(order, employee.getId(), accountId, now);
            order.setUpdatedAt(now);
            return mongo.save(order);
        });
        return detail(updated);
    }

    @Override
    public Detail transition(String accountId, String orderCode, Status target, String carrierName, String trackingCode) {
        OrderEntity updated = transactions.execute(() -> {
            Staff staff = access.require(accountId);
            OrderEntity order = orders.findOrder(orderCode);
            requireVisible(staff, order);
            if (target.name().equals(order.getStatus())) {
                return order;
            }

            Instant now = Instant.now();
            switch (target) {
                case CONFIRMED -> {
                    requireStatus(order, Status.PENDING);
                    requireReadyForPacking(order, now);
                    if (order.getAssignedEmployeeId() == null && staff.employeeId() != null) {
                        assign(order, staff.employeeId(), accountId, now);
                    }
                    if (order.getAssignedEmployeeId() == null) {
                        throw new ApiException(ApiError.ORDER_ASSIGNEE_REQUIRED);
                    }
                    order.setConfirmedAt(now);
                }
                case PACKING -> {
                    staff.requireAssigned(order);
                    requireStatus(order, Status.CONFIRMED);
                    requireReadyForPacking(order, now);
                    order.setPackingStartedAt(now);
                }
                case SHIPPING -> {
                    staff.requireAssigned(order);
                    requireStatus(order, Status.PACKED);
                    if (order.getStockIssuedAt() == null) {
                        throw new ApiException(ApiError.ORDER_NOT_ISSUABLE);
                    }
                    order.setShippedAt(now);
                    order.setCarrierName(clean(carrierName));
                    order.setTrackingCode(clean(trackingCode));
                }
                case DELIVERED -> {
                    staff.requireAssigned(order);
                    requireStatus(order, Status.SHIPPING);
                    order.setDeliveredAt(now);
                }
                case COMPLETED -> {
                    staff.requireAssigned(order);
                    requireStatus(order, Status.DELIVERED);
                    requirePaid(order);
                    order.setCompletedAt(now);
                }
                default -> throw new ApiException(ApiError.ORDER_STATUS_TRANSITION_INVALID);
            }
            order.setStatus(target.name());
            order.setLastStatusChangedByAccountId(accountId);
            order.setUpdatedAt(now);
            return mongo.save(order);
        });
        return detail(updated);
    }

    private void requireReadyForPacking(OrderEntity order, Instant now) {
        if (PaymentEntity.Status.PAID.name().equals(order.getPaymentStatus())) {
            return;
        }
        if (order.getPaymentMethod() != PaymentEntity.Method.COD
                || !PaymentEntity.Status.PENDING.name().equals(order.getPaymentStatus())) {
            throw new ApiException(ApiError.ORDER_PAYMENT_REQUIRED);
        }
        if (holds.expiredBeforeIssue(order, now)) {
            throw new ApiException(ApiError.STOCK_HOLD_EXPIRED);
        }
    }

    private static void requirePaid(OrderEntity order) {
        if (!PaymentEntity.Status.PAID.name().equals(order.getPaymentStatus())) {
            throw new ApiException(ApiError.ORDER_PAYMENT_REQUIRED);
        }
    }

    private static void requireVisible(Staff staff, OrderEntity order) {
        if (order.getAssignedEmployeeId() == null && Status.PENDING.name().equals(order.getStatus())) {
            return;
        }
        staff.requireAssigned(order);
    }

    private static void requireStatus(OrderEntity order, Status expected) {
        if (!expected.name().equals(order.getStatus())) {
            throw new ApiException(ApiError.ORDER_STATUS_TRANSITION_INVALID);
        }
    }

    private static void assign(OrderEntity order, String employeeId, String actor, Instant now) {
        order.setAssignedEmployeeId(employeeId);
        order.setAssignedByAccountId(actor);
        order.setAssignedAt(now);
    }

    private Detail detail(OrderEntity order) {
        EmployeeEntity employee = order.getAssignedEmployeeId() == null ? null
                : mongo.findById(order.getAssignedEmployeeId(), EmployeeEntity.class);
        PaymentEntity payment = mongo.findOne(Query.query(Criteria.where("orderId").is(order.getId())),
                PaymentEntity.class);
        PaymentSummary status = payment == null ? null : new PaymentSummary(payment.getPaymentCode(),
                payment.getStatus(), payment.getAmount(), payment.getPaidAt(), payment.getConfirmedByEmployeeId());
        var assignment = new Assignment(order.getAssignedEmployeeId(), employee == null ? null : employee.getFullName(),
                order.getAssignedByAccountId(), order.getAssignedAt());
        return new Detail(orders.detail(order), assignment, status, order.getLastStatusChangedByAccountId());
    }
}
