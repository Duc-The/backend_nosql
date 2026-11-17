package vn.ducthe.organic.dto.response;

import java.time.Instant;
import java.util.List;
import vn.ducthe.organic.common.response.PageData;

public final class ReviewResponses {
    private ReviewResponses() {}

    public record Media(String type, String url) {}
    public record Review(String id, String author, int rating, String comment, Instant createdAt, List<Media> media) {}
    public record RatingCount(int rating, long count) {}
    public record Feed(double average, long total, List<RatingCount> distribution, PageData<Review> reviews) {}
    public record ReviewForm(String productName, String productSlug, boolean alreadyReviewed) {}
}
