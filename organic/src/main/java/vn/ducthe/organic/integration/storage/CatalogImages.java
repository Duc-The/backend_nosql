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
import vn.ducthe.organic.dto.request.CatalogAdminPayloads;
import vn.ducthe.organic.entity.ProductEntity.Image;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;

@Component
public class CatalogImages {
    private static final Logger log = LoggerFactory.getLogger(CatalogImages.class);
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final Cloudinary cloudinary;
    private final String folder;
    private final boolean configured;

    CatalogImages(@Value("${app.cloudinary.cloud-name:}") String cloudName,
                  @Value("${app.cloudinary.api-key:}") String key,
                  @Value("${app.cloudinary.api-secret:}") String secret,
                  @Value("${app.cloudinary.folder-users:products}") String folder) {
        this.configured = !cloudName.isBlank() && !key.isBlank() && !secret.isBlank();
        this.cloudinary = new Cloudinary(Map.of("cloud_name", cloudName, "api_key", key, "api_secret", secret,
                "secure", true));
        this.folder = folder.strip().replaceAll("^/+|/+$", "");
        if (this.folder.isBlank()) {
            throw new IllegalArgumentException("Cloudinary product folder cannot be blank");
        }
    }

    public Image upload(String productId, MultipartFile file, CatalogAdminPayloads.ImageInput metadata) {
        if (!configured) {
            throw new ApiException(ApiError.CATALOG_IMAGE_PROVIDER);
        }
        if (file.isEmpty()) {
            throw new ApiException(ApiError.CATALOG_IMAGE_INVALID);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(ApiError.CATALOG_IMAGE_TOO_LARGE);
        }
        String publicId = prefix(productId) + UUID.randomUUID();
        try {
            byte[] bytes = file.getBytes();
            if (!isSupported(bytes)) {
                throw new ApiException(ApiError.CATALOG_IMAGE_INVALID);
            }
            Map<?, ?> result = cloudinary.uploader().upload(bytes, Map.of(
                    "resource_type", "image", "public_id", publicId, "overwrite", false,
                    "allowed_formats", List.of("jpg", "jpeg", "png", "webp"), "timeout", 15000));
            Object url = result.get("secure_url");
            if (!publicId.equals(result.get("public_id")) || !(url instanceof String secureUrl)
                    || !secureUrl.startsWith("https://")) {
                removeOwned(productId, publicId);
                throw new ApiException(ApiError.CATALOG_IMAGE_PROVIDER);
            }
            return new Image(secureUrl, publicId, metadata.altText(), metadata.sortOrder(), metadata.primary());
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            removeOwned(productId, publicId);
            log.warn("Cloudinary upload failed for product {}", productId);
            throw new ApiException(ApiError.CATALOG_IMAGE_PROVIDER);
        }
    }

    public void removeOwned(String productId, String publicId) {
        if (!configured || publicId == null || !publicId.startsWith(prefix(productId))) {
            return;
        }
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId,
                    Map.of("resource_type", "image", "invalidate", true, "timeout", 15000));
            if (!"ok".equals(result.get("result")) && !"not found".equals(result.get("result"))) {
                log.warn("Cloudinary did not confirm deletion of catalog image {}", publicId);
            }
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not remove catalog image {} from Cloudinary", publicId);
        }
    }

    private String prefix(String productId) {
        return folder + "/" + productId + "/";
    }

    private static boolean isSupported(byte[] bytes) {
        if (bytes.length < 12) {
            return false;
        }
        boolean jpeg = bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
        boolean png = bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
        boolean webp = bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        return jpeg || png || webp;
    }
}
