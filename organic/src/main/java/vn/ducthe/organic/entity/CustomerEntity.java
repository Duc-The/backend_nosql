package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Document(collection = "customers")
public class CustomerEntity extends BaseEntity {

    @Indexed(unique = true)
    private String accountId;
    @Indexed(unique = true)
    private String customerCode;
    private String fullName;
    private String phoneNumber;
    @Indexed(unique = true, sparse = true)
    private String email;
    private String avatarUrl;
    private String avatarPublicId;
    private LocalDate dateOfBirth;
    private Instant registeredAt;
    private String status;
    private long rewardPoints;

    @Version
    private Long version;
}
