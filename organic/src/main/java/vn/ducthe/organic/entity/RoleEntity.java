package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "roles")
public class RoleEntity extends BaseEntity {

    @Indexed(unique = true)
    private Code code;
    private String name;
    private String description;
    private String status;

    public enum Code {
        ADMIN, WAREHOUSE_STAFF, SALES_STAFF, CUSTOMER
    }
}
