package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "customer_addresses")
@CompoundIndex(name = "default_address_per_customer", def = "{'customerId': 1}", unique = true,
        partialFilter = "{'defaultAddress': true}")
public class CustomerAddressEntity extends BaseEntity {

    @Indexed
    private String customerId;
    private String label;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String district;
    private String ward;
    private String streetAddress;
    private boolean defaultAddress;
    private String status;
}
