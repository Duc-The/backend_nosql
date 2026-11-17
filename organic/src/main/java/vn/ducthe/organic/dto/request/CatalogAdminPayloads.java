package vn.ducthe.organic.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class CatalogAdminPayloads {
    private static final String CODE = "[A-Za-z0-9_-]+";
    private static final String SLUG = "[a-z0-9]+(?:-[a-z0-9]+)*";

    private CatalogAdminPayloads() {}

    public enum Status { ACTIVE, INACTIVE }

    public record CategoryInput(@NotBlank @Size(max = 64) @Pattern(regexp = CODE) String categoryCode,
                         @NotBlank @Size(max = 160) String name,
                         @NotBlank @Size(max = 160) @Pattern(regexp = SLUG) String slug,
                         @Size(max = 128) String parentCategoryId,
                         @Size(max = 2000) String description,
                         @Size(max = 2048) @Pattern(regexp = "https://[^\\s]+") String imageUrl,
                         @Min(0) int sortOrder, @NotNull Status status) {}

    public record ProductInput(@NotBlank @Size(max = 64) @Pattern(regexp = CODE) String productCode,
                        @NotBlank @Size(max = 200) String name,
                        @NotBlank @Size(max = 160) @Pattern(regexp = SLUG) String slug,
                        @NotBlank @Size(max = 128) String categoryId,
                        @Size(max = 120) String brandName,
                        @Size(max = 500) String originDescription,
                        @Size(max = 8000) String description,
                        @Size(max = 2000) String storageInstructions,
                        boolean featured, @Min(0) int featuredOrder, @NotNull Status status) {}

    public record VariantInput(@NotBlank @Size(max = 80) @Pattern(regexp = CODE) String sku,
                        @NotBlank @Size(max = 160) String name, @Size(max = 64) String barcode,
                        @NotNull @DecimalMin(value = "0", inclusive = false)
                        @Digits(integer = 9, fraction = 3) BigDecimal netQuantity,
                        @NotBlank @Size(max = 24) @Pattern(regexp = CODE) String unitCode,
                        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal sellingPrice,
                        @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal compareAtPrice,
                        @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 3) BigDecimal reorderLevel,
                        @NotNull Status status) {}

    public record StatusInput(@NotNull Status status) {}

    public record ImageInput(@Size(max = 250) String altText, @Min(0) int sortOrder, boolean primary) {}
}
