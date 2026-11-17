package vn.ducthe.organic.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import vn.ducthe.organic.dto.request.CatalogAdminPayloads;
import vn.ducthe.organic.entity.ProductEntity.Image;

public interface ProductImageService {

    List<Image> upload(String productId, MultipartFile file, CatalogAdminPayloads.ImageInput input);

    List<Image> update(String productId, String publicId, CatalogAdminPayloads.ImageInput input);

    void delete(String productId, String publicId);
}
