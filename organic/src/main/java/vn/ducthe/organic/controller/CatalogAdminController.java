package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.CategoryInput;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.ImageInput;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.ProductInput;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.Status;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.StatusInput;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads.VariantInput;
import vn.ducthe.organic.entity.CategoryEntity;
import vn.ducthe.organic.entity.ProductEntity;
import vn.ducthe.organic.entity.ProductEntity.Image;
import vn.ducthe.organic.entity.ProductVariantEntity;
import vn.ducthe.organic.service.CatalogAdminService;
import vn.ducthe.organic.service.ProductImageService;

@RestController
@RequestMapping("/api/v1/admin/catalog")
public class CatalogAdminController {
    private final CatalogAdminService catalog;
    private final ProductImageService images;

    CatalogAdminController(CatalogAdminService catalog, ProductImageService images) {
        this.catalog = catalog;
        this.images = images;
    }

    @GetMapping("/categories")
    ResponseEntity<ApiResponse<PageData<CategoryEntity>>> categories(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(catalog.categories(q, status, page, size), "Lấy danh sách danh mục thành công", request);
    }

    @GetMapping("/categories/{id}")
    ResponseEntity<ApiResponse<CategoryEntity>> category(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(catalog.category(id), "Lấy danh mục thành công", request);
    }

    @PostMapping("/categories")
    ResponseEntity<ApiResponse<CategoryEntity>> createCategory(@Valid @RequestBody CategoryInput body,
                                                               HttpServletRequest request) {
        return ApiResponse.created(catalog.createCategory(body), "Đã tạo danh mục", request);
    }

    @PutMapping("/categories/{id}")
    ResponseEntity<ApiResponse<CategoryEntity>> updateCategory(@PathVariable String id,
            @Valid @RequestBody CategoryInput body, HttpServletRequest request) {
        return ApiResponse.ok(catalog.updateCategory(id, body), "Đã cập nhật danh mục", request);
    }

    @PutMapping("/categories/{id}/status")
    ResponseEntity<ApiResponse<CategoryEntity>> categoryStatus(@PathVariable String id,
            @Valid @RequestBody StatusInput body, HttpServletRequest request) {
        return ApiResponse.ok(catalog.categoryStatus(id, body.status()), "Đã cập nhật trạng thái danh mục", request);
    }

    @GetMapping("/products")
    ResponseEntity<ApiResponse<PageData<ProductEntity>>> products(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) @Size(max = 128) String categoryId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(catalog.products(q, status, categoryId, page, size),
                "Lấy danh sách sản phẩm thành công", request);
    }

    @GetMapping("/products/{id}")
    ResponseEntity<ApiResponse<ProductEntity>> product(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(catalog.product(id), "Lấy sản phẩm thành công", request);
    }

    @PostMapping("/products")
    ResponseEntity<ApiResponse<ProductEntity>> createProduct(@Valid @RequestBody ProductInput body,
                                                             HttpServletRequest request) {
        return ApiResponse.created(catalog.createProduct(body), "Đã tạo sản phẩm", request);
    }

    @PutMapping("/products/{id}")
    ResponseEntity<ApiResponse<ProductEntity>> updateProduct(@PathVariable String id,
            @Valid @RequestBody ProductInput body, HttpServletRequest request) {
        return ApiResponse.ok(catalog.updateProduct(id, body), "Đã cập nhật sản phẩm", request);
    }

    @PutMapping("/products/{id}/status")
    ResponseEntity<ApiResponse<ProductEntity>> productStatus(@PathVariable String id,
            @Valid @RequestBody StatusInput body, HttpServletRequest request) {
        return ApiResponse.ok(catalog.productStatus(id, body.status()), "Đã cập nhật trạng thái sản phẩm", request);
    }

    @GetMapping("/products/{id}/variants")
    ResponseEntity<ApiResponse<PageData<ProductVariantEntity>>> variants(@PathVariable String id,
            @RequestParam(required = false) Status status, @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(catalog.variants(id, status, page, size), "Lấy danh sách SKU thành công", request);
    }

    @GetMapping("/products/{id}/variants/{variantId}")
    ResponseEntity<ApiResponse<ProductVariantEntity>> variant(@PathVariable String id, @PathVariable String variantId,
                                                               HttpServletRequest request) {
        return ApiResponse.ok(catalog.variantDetail(id, variantId), "Lấy SKU thành công", request);
    }

    @PostMapping("/products/{id}/variants")
    ResponseEntity<ApiResponse<ProductVariantEntity>> createVariant(@PathVariable String id,
            @Valid @RequestBody VariantInput body, HttpServletRequest request) {
        return ApiResponse.created(catalog.createVariant(id, body), "Đã tạo SKU", request);
    }

    @PutMapping("/products/{id}/variants/{variantId}")
    ResponseEntity<ApiResponse<ProductVariantEntity>> updateVariant(@PathVariable String id, @PathVariable String variantId,
            @Valid @RequestBody VariantInput body, HttpServletRequest request) {
        return ApiResponse.ok(catalog.updateVariant(id, variantId, body), "Đã cập nhật SKU", request);
    }

    @PutMapping("/products/{id}/variants/{variantId}/status")
    ResponseEntity<ApiResponse<ProductVariantEntity>> variantStatus(@PathVariable String id, @PathVariable String variantId,
            @Valid @RequestBody StatusInput body, HttpServletRequest request) {
        return ApiResponse.ok(catalog.variantStatus(id, variantId, body.status()), "Đã cập nhật trạng thái SKU", request);
    }

    @PostMapping(path = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApiResponse<List<Image>>> upload(@PathVariable String id, @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = 250) String altText,
            @RequestParam(defaultValue = "0") @Min(0) int sortOrder,
            @RequestParam(defaultValue = "false") boolean primary, HttpServletRequest request) {
        return ApiResponse.created(images.upload(id, file, new ImageInput(altText, sortOrder, primary)),
                "Đã tải ảnh sản phẩm", request);
    }

    @PutMapping("/products/{id}/images")
    ResponseEntity<ApiResponse<List<Image>>> updateImage(@PathVariable String id,
            @RequestParam @NotBlank @Size(max = 512) String publicId, @Valid @RequestBody ImageInput body,
            HttpServletRequest request) {
        return ApiResponse.ok(images.update(id, publicId, body), "Đã cập nhật ảnh sản phẩm", request);
    }

    @DeleteMapping("/products/{id}/images")
    ResponseEntity<Void> deleteImage(@PathVariable String id, @RequestParam @NotBlank @Size(max = 512) String publicId) {
        images.delete(id, publicId);
        return ResponseEntity.noContent().build();
    }
}
