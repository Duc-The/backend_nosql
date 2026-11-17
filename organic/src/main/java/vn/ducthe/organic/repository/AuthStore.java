package vn.ducthe.organic.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import vn.ducthe.organic.entity.AccountEntity;
import vn.ducthe.organic.entity.AccountIdentityEntity;
import vn.ducthe.organic.entity.AccountRoleEntity;
import vn.ducthe.organic.entity.BaseEntity;
import vn.ducthe.organic.entity.CustomerEntity;
import vn.ducthe.organic.entity.RoleEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;

@Component
public class AuthStore {
    public static final String ACTIVE = "ACTIVE";
    public static final String PENDING = "PENDING_VERIFICATION";
    private final MongoTemplate mongo;

    public AuthStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public AccountEntity byEmail(String email) {
        CustomerEntity customer = mongo.findOne(query("email", email), CustomerEntity.class);
        return customer == null ? null : mongo.findById(customer.getAccountId(), AccountEntity.class);
    }

    public AccountEntity byUsername(String username) {
        return mongo.findOne(query("username", username), AccountEntity.class);
    }

    public AccountEntity activeAccount(String id) {
        AccountEntity account = mongo.findById(id, AccountEntity.class);
        return account != null && ACTIVE.equals(account.getStatus()) ? account : null;
    }

    public CustomerEntity customer(String accountId) {
        CustomerEntity customer = mongo.findOne(query("accountId", accountId), CustomerEntity.class);
        if (customer == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return customer;
    }

    public AccountIdentityEntity googleIdentity(String subject) {
        return mongo.findOne(Query.query(Criteria.where("provider").is("GOOGLE")
                .and("providerSubject").is(subject)), AccountIdentityEntity.class);
    }

    public AccountEntity create(String username, String email, String fullName, String passwordHash,
                                String googleSubject) {
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        RoleEntity role = customerRole(now);
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setUsername(username);
        account.setPasswordHash(passwordHash);
        account.setPasswordChangedAt(now);
        account.setStatus(googleSubject == null ? PENDING : ACTIVE);
        stamp(account, now);
        CustomerEntity customer = new CustomerEntity();
        customer.setAccountId(id);
        customer.setCustomerCode("KH-" + id);
        customer.setFullName(fullName);
        customer.setEmail(email);
        customer.setRegisteredAt(now);
        customer.setStatus(account.getStatus());
        stamp(customer, now);

        try {
            mongo.insert(account);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(ApiError.CONFLICT);
        }
        try {
            mongo.insert(customer);
            AccountRoleEntity assignment = new AccountRoleEntity();
            assignment.setAccountId(id);
            assignment.setRoleId(role.getId());
            assignment.setStatus(ACTIVE);
            assignment.setAssignedAt(now);
            stamp(assignment, now);
            mongo.insert(assignment);
            if (googleSubject != null) {
                AccountIdentityEntity identity = new AccountIdentityEntity();
                identity.setAccountId(id);
                identity.setProvider("GOOGLE");
                identity.setProviderSubject(googleSubject);
                identity.setProviderEmail(email);
                identity.setLinkedAt(now);
                stamp(identity, now);
                mongo.insert(identity);
            }
        } catch (RuntimeException exception) {
            mongo.remove(query("accountId", id), AccountRoleEntity.class);
            mongo.remove(query("accountId", id), CustomerEntity.class);
            mongo.remove(query("_id", id), AccountEntity.class);
            if (exception instanceof DuplicateKeyException) {
                throw new ApiException(ApiError.CONFLICT);
            }
            throw exception;
        }
        return account;
    }

    public void activate(AccountEntity account) {
        Instant now = Instant.now();
        mongo.updateFirst(query("accountId", account.getId()), new Update().set("status", ACTIVE)
                .set("updatedAt", now), CustomerEntity.class);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(account.getId()).and("status").is(PENDING)),
                new Update().set("status", ACTIVE).set("updatedAt", now), AccountEntity.class);
    }

    public void updatePassword(AccountEntity account, String hash) {
        Instant changed = account.getPasswordChangedAt();
        long nextVersion = Math.max(System.currentTimeMillis(), Long.parseLong(credentialVersion(account)) + 1);
        Instant now = Instant.ofEpochMilli(nextVersion);
        Query query = Query.query(Criteria.where("_id").is(account.getId()).and("status").is(ACTIVE)
                .and("passwordChangedAt").is(changed));
        if (mongo.updateFirst(query, new Update().set("passwordHash", hash).set("passwordChangedAt", now)
                .set("updatedAt", now), AccountEntity.class).getModifiedCount() != 1) {
            throw new ApiException(ApiError.CONFLICT);
        }
    }

    public void recordLogin(String id) {
        mongo.updateFirst(query("_id", id), new Update().set("lastLoginAt", Instant.now()), AccountEntity.class);
    }

    public static String credentialVersion(AccountEntity account) {
        long password = account.getPasswordChangedAt() == null ? 0 : account.getPasswordChangedAt().toEpochMilli();
        long revoked = account.getSessionsRevokedAt() == null ? 0 : account.getSessionsRevokedAt().toEpochMilli();
        return Long.toString(Math.max(password, revoked));
    }

    private RoleEntity customerRole(Instant now) {
        Query query = query("code", RoleEntity.Code.CUSTOMER);
        RoleEntity role = mongo.findOne(query, RoleEntity.class);
        if (role != null) {
            return role;
        }
        role = new RoleEntity();
        role.setCode(RoleEntity.Code.CUSTOMER);
        role.setName("Khách hàng");
        role.setStatus(ACTIVE);
        stamp(role, now);
        try {
            return mongo.insert(role);
        } catch (DuplicateKeyException exception) {
            RoleEntity existing = mongo.findOne(query, RoleEntity.class);
            if (existing == null) {
                throw exception;
            }
            return existing;
        }
    }

    private static Query query(String field, Object value) {
        return Query.query(Criteria.where(field).is(value));
    }

    private static void stamp(BaseEntity entity, Instant now) {
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
    }
}
