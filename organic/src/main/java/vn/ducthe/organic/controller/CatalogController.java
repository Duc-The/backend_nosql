package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.CatalogResponses;
import vn.ducthe.organic.service.CatalogService;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    ResponseEntity<ApiResponse<List<CatalogResponses.Category>>> categories(HttpServletRequest request) {
        return ApiResponse.ok(catalog.categories(), "Lấy danh mục thành công", request);
    }

    @GetMapping("/products")
    ResponseEntity<ApiResponse<PageData<CatalogResponses.Product>>> products(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 160) String categorySlug,
            @RequestParam(required = false) @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean featured,
            @RequestParam(required = false) CatalogService.Shelf shelf,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        var products = catalog.products(q, categorySlug, minPrice, maxPrice, featured, shelf, page, size);
        return ApiResponse.ok(products, "Lấy danh sách sản phẩm thành công", request);
    }

    @GetMapping("/products/{slug}")
    ResponseEntity<ApiResponse<CatalogResponses.ProductDetail>> detail(
            @PathVariable @Size(max = 160) String slug, HttpServletRequest request) {
        return ApiResponse.ok(catalog.productDetail(slug), "Lấy chi tiết sản phẩm thành công", request);
    }
}
