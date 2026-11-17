package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "suppliers")
public class SupplierEntity extends BaseEntity {

    @Indexed(unique = true)
    private String supplierCode;
    private String name;
    private String taxCode;
    private String contactPerson;
    private String phoneNumber;
    private String email;
    private String address;
    private String status;
    private String note;
}
