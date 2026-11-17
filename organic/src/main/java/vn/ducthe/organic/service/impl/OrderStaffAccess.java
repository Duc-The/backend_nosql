package vn.ducthe.organic.service.impl;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import vn.ducthe.organic.entity.EmployeeEntity;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.RoleEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.service.impl.RoleAccess;

@Component
public class OrderStaffAccess {
    private final MongoDocumentRepository mongo;
    private final AuthStore accounts;
    private final RoleAccess roles;

    OrderStaffAccess(MongoDocumentRepository mongo, AuthStore accounts, RoleAccess roles) {
        this.mongo = mongo;
        this.accounts = accounts;
        this.roles = roles;
    }

    public Staff require(String accountId) {
        var authorities = roles.roles(accountId);
        boolean admin = authorities.contains(RoleEntity.Code.ADMIN);
        if (accounts.activeAccount(accountId) == null || !admin && !authorities.contains(RoleEntity.Code.SALES_STAFF)) {
            throw new ApiException(ApiError.ACCESS_DENIED);
        }
        EmployeeEntity employee = mongo.findOne(Query.query(Criteria.where("accountId").is(accountId)
                .and("workStatus").is(AuthStore.ACTIVE)), EmployeeEntity.class);
        if (employee == null && !admin) {
            throw new ApiException(ApiError.ACCESS_DENIED);
        }
        return new Staff(admin, employee == null ? null : employee.getId());
    }

    EmployeeEntity assignee(String id) {
        EmployeeEntity employee = mongo.findById(id, EmployeeEntity.class);
        if (employee == null || !AuthStore.ACTIVE.equals(employee.getWorkStatus())
                || accounts.activeAccount(employee.getAccountId()) == null) {
            throw new ApiException(ApiError.ORDER_ASSIGNEE_INVALID);
        }
        var authorities = roles.roles(employee.getAccountId());
        if (!authorities.contains(RoleEntity.Code.SALES_STAFF) && !authorities.contains(RoleEntity.Code.ADMIN)) {
            throw new ApiException(ApiError.ORDER_ASSIGNEE_INVALID);
        }
        return employee;
    }

    public record Staff(boolean admin, String employeeId) {
        public void requireAssigned(OrderEntity order) {
            if (!admin && !employeeId.equals(order.getAssignedEmployeeId())) {
                throw new ApiException(ApiError.ACCESS_DENIED);
            }
        }
    }
}
