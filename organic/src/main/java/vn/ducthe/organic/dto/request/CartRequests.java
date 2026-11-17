package vn.ducthe.organic.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

public final class CartRequests {
    private static final String ID = "[a-zA-Z0-9_-]{1,128}";

    private CartRequests() {}

    public record AddItem(@NotBlank @Pattern(regexp = ID) String variantId, @Min(1) @Max(99) int quantity) {}

    public record PromotionInput(@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String code) {}

    public record Quantity(@Min(1) @Max(99) int quantity) {}

    public record DeliveryAddress(@NotBlank @Pattern(regexp = ID) String addressId) {}
}
