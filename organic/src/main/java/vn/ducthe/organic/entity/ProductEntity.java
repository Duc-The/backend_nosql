package vn.ducthe.organic.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Document(collection = "products")
@CompoundIndex(name = "categoryId_status", def = "{'categoryId': 1, 'status': 1}")
public class ProductEntity extends BaseEntity {

    @Indexed(unique = true)
    private String productCode;
    private String name;
    @Indexed(unique = true)
    private String slug;
    private String categoryId;
    private String brandName;
    private String originDescription;
    private String description;
    private String storageInstructions;
    private List<Image> images = new ArrayList<>();
    @JsonIgnore
    private Long imagesRevision;
    private String status;
    private boolean featured;
    private int featuredOrder;

    public record Image(String url, String publicId, String altText, int sortOrder, boolean primary) {
    }
}
