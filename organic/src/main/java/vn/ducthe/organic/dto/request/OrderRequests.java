package vn.ducthe.organic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.entity.PaymentEntity;

public final class OrderRequests {
    private OrderRequests() {}

    public record Checkout(@Size(max = 500) String note, PaymentEntity.Method paymentMethod) {}

    public record Cancel(@NotBlank @Size(max = 500) String reason) {}
}
