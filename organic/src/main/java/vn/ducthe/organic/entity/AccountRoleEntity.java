package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "account_roles")
@CompoundIndex(name = "accountId_roleId", def = "{'accountId': 1, 'roleId': 1}", unique = true)
public class AccountRoleEntity extends BaseEntity {

    private String accountId;
    @Indexed
    private String roleId;
    private String status;
    private String assignedByAccountId;
    private Instant assignedAt;
}
