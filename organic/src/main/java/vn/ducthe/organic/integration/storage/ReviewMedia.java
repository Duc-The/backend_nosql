package vn.ducthe.organic.integration.storage;

import com.cloudinary.Cloudinary;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import vn.ducthe.organic.entity.ProductReviewEntity.Media;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;

@Component
public class ReviewMedia {
    private static final Logger log = LoggerFactory.getLogger(ReviewMedia.class);
    private static final long MB = 1024 * 1024;
    private final Cloudinary cloudinary;
    private final boolean configured;

    ReviewMedia(@Value("${app.cloudinary.cloud-name:}") String name,
                @Value("${app.cloudinary.api-key:}") String key,
                @Value("${app.cloudinary.api-secret:}") String secret) {
        configured = !name.isBlank() && !key.isBlank() && !secret.isBlank();
        cloudinary = new Cloudinary(Map.of("cloud_name", name, "api_key", key, "api_secret", secret, "secure", true));
    }

    public void validate(MultipartFile file, boolean video) {
        if (file.isEmpty()) throw new ApiException(ApiError.REVIEW_MEDIA_INVALID);
        if (file.getSize() > (video ? 25 : 5) * MB) throw new ApiException(ApiError.REVIEW_MEDIA_TOO_LARGE);
        try (var stream = file.getInputStream()) {
            byte[] head = stream.readNBytes(12);
            if (head.length < 12 || !(video ? isVideo(head) : isImage(head))) {
                throw new ApiException(ApiError.REVIEW_MEDIA_INVALID);
            }
        } catch (IOException exception) {
            throw new ApiException(ApiError.REVIEW_MEDIA_INVALID);
        }
    }

    private static boolean isVideo(byte[] head) {
        return matches(head, 4, 'f', 't', 'y', 'p') || matches(head, 0, 0x1a, 0x45, 0xdf, 0xa3);
    }

    private static boolean isImage(byte[] head) {
        return matches(head, 0, 0xff, 0xd8, 0xff)
                || matches(head, 0, 0x89, 'P', 'N', 'G', 13, 10, 26, 10)
                || (matches(head, 0, 'R', 'I', 'F', 'F') && matches(head, 8, 'W', 'E', 'B', 'P'));
    }

    private static boolean matches(byte[] bytes, int start, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            if (Byte.toUnsignedInt(bytes[start + i]) != expected[i]) return false;
        }
        return true;
    }

    public Media upload(MultipartFile file, boolean video) {
        if (!configured) throw new ApiException(ApiError.REVIEW_MEDIA_UNAVAILABLE);
        String type = video ? "video" : "image";
        String id = "product-reviews/" + UUID.randomUUID();
        try {
            var formats = video ? List.of("mp4", "webm") : List.of("jpg", "jpeg", "png", "webp");
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), Map.of("resource_type", type,
                    "public_id", id, "overwrite", false, "allowed_formats", formats, "timeout", 30000));
            if (id.equals(result.get("public_id")) && result.get("secure_url") instanceof String url
                    && url.startsWith("https://")) {
                return new Media(type, url, id);
            }
        } catch (IOException | RuntimeException exception) {
            log.warn("Review media upload failed for {}", id);
        }
        remove(new Media(type, null, id));
        throw new ApiException(ApiError.REVIEW_MEDIA_UNAVAILABLE);
    }

    public void remove(Media media) {
        try {
            cloudinary.uploader().destroy(media.publicId(),
                    Map.of("resource_type", media.type(), "invalidate", true, "timeout", 10000));
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not remove review media {}", media.publicId());
        }
    }
}
