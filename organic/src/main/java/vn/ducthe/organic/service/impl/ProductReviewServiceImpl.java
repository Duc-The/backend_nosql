package vn.ducthe.organic.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.ProductReviewRequest;
import vn.ducthe.organic.dto.response.ReviewResponses.Feed;
import vn.ducthe.organic.dto.response.ReviewResponses.RatingCount;
import vn.ducthe.organic.dto.response.ReviewResponses.Review;
import vn.ducthe.organic.dto.response.ReviewResponses.ReviewForm;
import vn.ducthe.organic.entity.CustomerEntity;
import vn.ducthe.organic.entity.OrderDetailEntity;
import vn.ducthe.organic.entity.ProductReviewEntity;
import vn.ducthe.organic.entity.ProductReviewEntity.Media;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.integration.storage.ReviewMedia;
import vn.ducthe.organic.mapper.ProductReviewMapper;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.ProductReviewRepository;
import vn.ducthe.organic.service.CatalogService;
import vn.ducthe.organic.service.ProductReviewService;

@Service
public class ProductReviewServiceImpl implements ProductReviewService {
    private final ProductReviewRepository reviews;
    private final AuthStore auth;
    private final CatalogService catalog;
    private final ReviewMedia media;

    public ProductReviewServiceImpl(ProductReviewRepository reviews, AuthStore auth, CatalogService catalog, ReviewMedia media) {
        this.reviews = reviews;
        this.auth = auth;
        this.catalog = catalog;
        this.media = media;
    }

    @Override
    public Feed feed(String slug, int page, int size) {
        String productId = catalog.productDetail(slug).id();
        var counts = new HashMap<Integer, Long>();
        for (var row : reviews.counts(productId)) {
            counts.put(row.getInteger("_id"), ((Number) row.get("count")).longValue());
        }
        List<RatingCount> distribution = IntStream.rangeClosed(1, 5)
                .mapToObj(rating -> new RatingCount(rating, counts.getOrDefault(rating, 0L))).toList();
        long total = distribution.stream().mapToLong(RatingCount::count).sum();
        long points = distribution.stream().mapToLong(row -> row.rating() * row.count()).sum();
        return new Feed(total == 0 ? 0 : (double) points / total, total, distribution,
                PageData.from(reviews.page(productId, page, size, total).map(ProductReviewMapper::toReview)));
    }

    @Override
    public ReviewForm form(String accountId, String orderCode, String productId) {
        var purchase = purchase(accountId, orderCode, productId);
        var product = reviews.product(productId);
        return new ReviewForm(purchase.line().getProductName(), product == null ? null : product.getSlug(),
                reviews.exists(purchase.customer().getId(), productId));
    }

    @Override
    public Review create(String accountId, String orderCode, String productId, ProductReviewRequest input,
                         List<MultipartFile> photos, MultipartFile video) {
        var purchase = purchase(accountId, orderCode, productId);
        String customerId = purchase.customer().getId();
        if (reviews.exists(customerId, productId)) throw new ApiException(ApiError.REVIEW_ALREADY_EXISTS);
        List<MultipartFile> images = photos == null ? List.of() : photos;
        if (images.size() > 4) throw new ApiException(ApiError.REVIEW_MEDIA_INVALID);
        images.forEach(file -> media.validate(file, false));
        if (video != null) media.validate(video, true);

        var row = new ProductReviewEntity();
        row.setId(UUID.nameUUIDFromBytes((customerId + "\n" + productId).getBytes(StandardCharsets.UTF_8)).toString());
        row.setCustomerId(customerId);
        row.setProductId(productId);
        row.setAuthor(ProductReviewMapper.author(purchase.customer().getFullName()));
        row.setRating(input.rating());
        row.setComment(input.comment() == null ? null : input.comment().strip());
        row.setCreatedAt(Instant.now());
        row.setUpdatedAt(row.getCreatedAt());

        var uploads = new ArrayList<Media>();
        try {
            for (var file : images) uploads.add(media.upload(file, false));
            if (video != null) uploads.add(media.upload(video, true));
        } catch (RuntimeException exception) {
            uploads.forEach(media::remove);
            throw exception;
        }
        row.setMedia(List.copyOf(uploads));
        try {
            return ProductReviewMapper.toReview(reviews.insert(row));
        } catch (DuplicateKeyException exception) {
            uploads.forEach(media::remove);
            throw new ApiException(ApiError.REVIEW_ALREADY_EXISTS);
        }
    }

    private Purchase purchase(String accountId, String orderCode, String productId) {
        CustomerEntity customer = auth.customer(accountId);
        var order = reviews.order(customer.getId(), orderCode);
        if (order == null) throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        if (!"COMPLETED".equals(order.getStatus()) || !"PAID".equals(order.getPaymentStatus())) {
            throw new ApiException(ApiError.REVIEW_PURCHASE_REQUIRED);
        }
        var line = reviews.purchasedProduct(order.getId(), productId);
        if (line == null) throw new ApiException(ApiError.REVIEW_PURCHASE_REQUIRED);
        return new Purchase(customer, line);
    }

    private record Purchase(CustomerEntity customer, OrderDetailEntity line) {}
}
