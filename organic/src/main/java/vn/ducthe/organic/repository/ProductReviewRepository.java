package vn.ducthe.organic.repository;

import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import vn.ducthe.organic.entity.OrderDetailEntity;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.ProductEntity;
import vn.ducthe.organic.entity.ProductReviewEntity;

@Repository
public class ProductReviewRepository {
    private final MongoTemplate mongo;

    public ProductReviewRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public OrderEntity order(String customerId, String orderCode) {
        return mongo.findOne(Query.query(Criteria.where("customerId").is(customerId).and("orderCode").is(orderCode)),
                OrderEntity.class);
    }

    public OrderDetailEntity purchasedProduct(String orderId, String productId) {
        return mongo.findOne(Query.query(Criteria.where("orderId").is(orderId).and("productId").is(productId)),
                OrderDetailEntity.class);
    }

    public ProductEntity product(String productId) {
        return mongo.findById(productId, ProductEntity.class);
    }

    public boolean exists(String customerId, String productId) {
        return mongo.exists(Query.query(Criteria.where("customerId").is(customerId).and("productId").is(productId)),
                ProductReviewEntity.class);
    }

    public ProductReviewEntity insert(ProductReviewEntity review) {
        return mongo.insert(review);
    }

    public List<Document> counts(String productId) {
        var query = Aggregation.newAggregation(ProductReviewEntity.class,
                Aggregation.match(Criteria.where("productId").is(productId)),
                Aggregation.group("rating").count().as("count"));
        return mongo.aggregate(query, ProductReviewEntity.class, Document.class).getMappedResults();
    }

    public Page<ProductReviewEntity> page(String productId, int page, int size, long total) {
        var pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var rows = mongo.find(Query.query(Criteria.where("productId").is(productId)).with(pageable), ProductReviewEntity.class);
        return new PageImpl<>(rows, pageable, total);
    }
}
