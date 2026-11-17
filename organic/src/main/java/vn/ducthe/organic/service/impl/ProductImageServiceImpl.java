package vn.ducthe.organic.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads;
import vn.ducthe.organic.entity.ProductEntity;
import vn.ducthe.organic.entity.ProductEntity.Image;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.integration.storage.CatalogImages;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.service.CatalogAdminService;
import vn.ducthe.organic.service.ProductImageService;

@Service
public class ProductImageServiceImpl implements ProductImageService {
    private static final int IMAGE_LIMIT = 12;
    private final MongoDocumentRepository mongo;
    private final CatalogAdminService catalog;
    private final CatalogImages provider;

    public ProductImageServiceImpl(MongoDocumentRepository mongo, CatalogAdminService catalog, CatalogImages provider) {
        this.mongo = mongo;
        this.catalog = catalog;
        this.provider = provider;
    }

    @Override
    public List<Image> upload(String productId, MultipartFile file, CatalogAdminPayloads.ImageInput input) {
        checkLimit(images(catalog.product(productId)));
        Image image = provider.upload(productId, file, input);
        try {
            return change(productId, items -> {
                checkLimit(items);
                items.add(image);
                if (image.primary()) {
                    makePrimary(items, items.size() - 1);
                }
            });
        } catch (ApiException exception) {
            // Business errors occur before the upload can be referenced by a committed catalog update.
            provider.removeOwned(productId, image.publicId());
            throw exception;
        }
    }

    @Override
    public List<Image> update(String productId, String publicId, CatalogAdminPayloads.ImageInput input) {
        return change(productId, items -> {
            int index = indexOf(items, publicId);
            Image current = items.get(index);
            items.set(index, new Image(current.url(), current.publicId(), input.altText(), input.sortOrder(), input.primary()));
            if (input.primary()) {
                makePrimary(items, index);
            }
        });
    }

    @Override
    public void delete(String productId, String publicId) {
        change(productId, items -> items.remove(indexOf(items, publicId)));
        provider.removeOwned(productId, publicId);
    }

    private List<Image> change(String productId, Consumer<List<Image>> edit) {
        for (int attempt = 0; attempt < 3; attempt++) {
            ProductEntity product = catalog.product(productId);
            List<Image> next = images(product);
            edit.accept(next);
            normalizePrimary(next);
            Long revision = product.getImagesRevision();
            Query filter = Query.query(Criteria.where("_id").is(productId).and("imagesRevision").is(revision));
            Update update = new Update().set("images", next).set("updatedAt", Instant.now())
                    .set("imagesRevision", revision == null ? 1L : revision + 1);
            if (mongo.updateFirst(filter, update, ProductEntity.class).getMatchedCount() == 1) {
                return List.copyOf(next);
            }
        }
        throw new ApiException(ApiError.CONFLICT);
    }

    private static List<Image> images(ProductEntity product) {
        return product.getImages() == null ? new ArrayList<>() : new ArrayList<>(product.getImages());
    }

    private static int indexOf(List<Image> images, String publicId) {
        for (int index = 0; index < images.size(); index++) {
            if (Objects.equals(publicId, images.get(index).publicId())) {
                return index;
            }
        }
        throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
    }

    private static void checkLimit(List<Image> images) {
        if (images.size() >= IMAGE_LIMIT) {
            throw new ApiException(ApiError.CATALOG_IMAGE_LIMIT);
        }
    }

    private static void normalizePrimary(List<Image> images) {
        images.sort(Comparator.comparingInt(Image::sortOrder));
        for (int index = 0; index < images.size(); index++) {
            if (images.get(index).primary()) {
                makePrimary(images, index);
                return;
            }
        }
        if (!images.isEmpty()) {
            makePrimary(images, 0);
        }
    }

    private static void makePrimary(List<Image> images, int primary) {
        for (int index = 0; index < images.size(); index++) {
            Image image = images.get(index);
            boolean selected = index == primary;
            if (image.primary() != selected) {
                images.set(index, new Image(image.url(), image.publicId(), image.altText(), image.sortOrder(), selected));
            }
        }
    }
}
