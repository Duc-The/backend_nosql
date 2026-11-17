package vn.ducthe.organic.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import vn.ducthe.organic.entity.PaymentEntity;

public final class SalesOrderResponses {
    private SalesOrderResponses() {}

    public record Row(String orderCode, String status, PaymentEntity.Method paymentMethod, String paymentStatus,
               BigDecimal totalAmount, String currency, Instant orderedAt, String assignedEmployeeId,
               String assignedEmployeeName) {}

    public record Detail(OrderResponses.Detail order, Assignment assignment, PaymentSummary payment,
                  String lastStatusChangedByAccountId) {}

    public record Assignment(String employeeId, String employeeName, String assignedByAccountId, Instant assignedAt) {}

    public record PaymentSummary(String paymentCode, PaymentEntity.Status status, BigDecimal amount, Instant paidAt,
                          String confirmedByEmployeeId) {}
}
