package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "account_identities")
@CompoundIndex(name = "provider_providerSubject", def = "{'provider': 1, 'providerSubject': 1}", unique = true)
@CompoundIndex(name = "accountId_provider", def = "{'accountId': 1, 'provider': 1}", unique = true)
public class AccountIdentityEntity extends BaseEntity {

    private String accountId;
    private String provider;
    private String providerSubject;
    private String providerEmail;
    private Instant linkedAt;
    private Instant lastUsedAt;
}
