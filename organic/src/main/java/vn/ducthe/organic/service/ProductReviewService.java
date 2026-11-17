package vn.ducthe.organic.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import vn.ducthe.organic.dto.request.ProductReviewRequest;
import vn.ducthe.organic.dto.response.ReviewResponses.Feed;
import vn.ducthe.organic.dto.response.ReviewResponses.Review;
import vn.ducthe.organic.dto.response.ReviewResponses.ReviewForm;

public interface ProductReviewService {
    Feed feed(String slug, int page, int size);
    ReviewForm form(String accountId, String orderCode, String productId);
    Review create(String accountId, String orderCode, String productId, ProductReviewRequest input,
                  List<MultipartFile> photos, MultipartFile video);
}
