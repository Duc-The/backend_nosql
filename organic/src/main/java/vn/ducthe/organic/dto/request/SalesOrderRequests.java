package vn.ducthe.organic.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.service.SalesOrderService.Status;

public final class SalesOrderRequests {
    private SalesOrderRequests() {}

    public record AssignmentInput(@Size(max = 128) String employeeId) {}

    public record StatusInput(@NotNull Status status, @Size(max = 120) String carrierName,
                       @Size(max = 128) String trackingCode) {}
}
