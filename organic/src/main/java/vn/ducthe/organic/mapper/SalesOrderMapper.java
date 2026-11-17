package vn.ducthe.organic.mapper;

import vn.ducthe.organic.dto.response.SalesOrderResponses.Row;
import vn.ducthe.organic.entity.EmployeeEntity;
import vn.ducthe.organic.entity.OrderEntity;

public final class SalesOrderMapper {
    private SalesOrderMapper() {}

    public static Row row(OrderEntity order, EmployeeEntity employee) {
        return new Row(order.getOrderCode(), order.getStatus(), order.getPaymentMethod(), order.getPaymentStatus(),
                order.getTotalAmount(), order.getCurrency(), order.getOrderedAt(), order.getAssignedEmployeeId(),
                employee == null ? null : employee.getFullName());
    }

    public static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
