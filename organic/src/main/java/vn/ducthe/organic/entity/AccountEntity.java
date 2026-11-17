package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "accounts")
public class AccountEntity extends BaseEntity {

    @Indexed(unique = true)
    private String username;
    private String passwordHash;
    private String status;
    private Instant lastLoginAt;
    private Instant passwordChangedAt;
    private Instant sessionsRevokedAt;
}
