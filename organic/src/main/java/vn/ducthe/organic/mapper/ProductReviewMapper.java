package vn.ducthe.organic.mapper;

import vn.ducthe.organic.dto.response.ReviewResponses.Media;
import vn.ducthe.organic.dto.response.ReviewResponses.Review;
import vn.ducthe.organic.entity.ProductReviewEntity;

public final class ProductReviewMapper {
    private ProductReviewMapper() {}

    public static Review toReview(ProductReviewEntity entity) {
        return new Review(entity.getId(), entity.getAuthor(), entity.getRating(), entity.getComment(),
                entity.getCreatedAt(), entity.getMedia().stream().map(media -> new Media(media.type(), media.url())).toList());
    }

    public static String author(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Khách hàng";
        }
        String[] words = fullName.strip().split("\\s+");
        String first = words[0];
        return new String(Character.toChars(first.codePointAt(0))) + "•••";
    }
}
