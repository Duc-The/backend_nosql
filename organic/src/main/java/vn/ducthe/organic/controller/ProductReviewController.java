package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.ducthe.organic.annotation.CurrentJwt;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.dto.request.ProductReviewRequest;
import vn.ducthe.organic.dto.response.ReviewResponses.Feed;
import vn.ducthe.organic.dto.response.ReviewResponses.Eligibility;
import vn.ducthe.organic.dto.response.ReviewResponses.Review;
import vn.ducthe.organic.dto.response.ReviewResponses.ReviewForm;
import vn.ducthe.organic.service.ProductReviewService;

@RestController
@RequestMapping("/api/v1")
public class ProductReviewController {
    private final ProductReviewService reviews;

    public ProductReviewController(ProductReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/catalog/products/{slug}/reviews")
    ResponseEntity<ApiResponse<Feed>> feed(@PathVariable @Size(max = 160) String slug,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "8") @Min(1) @Max(24) int size, HttpServletRequest request) {
        return ApiResponse.ok(reviews.feed(slug, page, size), "Lấy đánh giá sản phẩm thành công", request);
    }

    @GetMapping("/orders/review-eligibility/{slug}")
    ResponseEntity<ApiResponse<Eligibility>> eligibility(@CurrentJwt Jwt jwt,
            @PathVariable @Size(max = 160) String slug, HttpServletRequest request) {
        return ApiResponse.ok(reviews.eligibility(jwt.getSubject(), slug), "Kiểm tra quyền đánh giá thành công", request);
    }

    @GetMapping("/orders/{orderCode}/reviews/{productId}")
    ResponseEntity<ApiResponse<ReviewForm>> form(@CurrentJwt Jwt jwt,
            @PathVariable @Size(max = 128) String orderCode, @PathVariable @Size(max = 128) String productId,
            HttpServletRequest request) {
        return ApiResponse.ok(reviews.form(jwt.getSubject(), orderCode, productId), "Lấy thông tin đánh giá thành công", request);
    }

    @PostMapping(path = "/orders/{orderCode}/reviews/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApiResponse<Review>> create(@CurrentJwt Jwt jwt,
            @PathVariable @Size(max = 128) String orderCode, @PathVariable @Size(max = 128) String productId,
            @Valid @RequestPart("review") ProductReviewRequest input,
            @RequestPart(name = "photos", required = false) List<MultipartFile> photos,
            @RequestPart(name = "video", required = false) MultipartFile video, HttpServletRequest request) {
        return ApiResponse.created(reviews.create(jwt.getSubject(), orderCode, productId, input, photos, video),
                "Đã gửi đánh giá", request);
    }
}
