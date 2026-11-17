package vn.ducthe.organic.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.service.InventoryService.BatchStatus;

public final class InventoryRequests {
    private InventoryRequests() {}

    public record StatusInput(@NotNull BatchStatus status, @Size(max = 500) String reason) {}
}
