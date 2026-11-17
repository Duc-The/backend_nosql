package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "categories")
public class CategoryEntity extends BaseEntity {

    @Indexed(unique = true)
    private String categoryCode;
    private String name;
    @Indexed(unique = true)
    private String slug;
    @Indexed
    private String parentCategoryId;
    private String description;
    private String imageUrl;
    private String status;
    private int sortOrder;
}
