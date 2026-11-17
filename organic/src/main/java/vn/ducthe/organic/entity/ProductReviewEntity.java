package vn.ducthe.organic.entity;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document("product_reviews")
@CompoundIndex(name = "one_review_per_customer_product", def = "{'customerId': 1, 'productId': 1}", unique = true)
@CompoundIndex(name = "product_review_feed", def = "{'productId': 1, 'createdAt': -1, '_id': -1}")
public class ProductReviewEntity extends BaseEntity {
    private String customerId;
    private String productId;
    private String author;
    private int rating;
    private String comment;
    private List<Media> media = List.of();

    public record Media(String type, String url, String publicId) {}
}
