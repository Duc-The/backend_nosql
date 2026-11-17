package vn.ducthe.organic.service.impl;

import static vn.ducthe.organic.mapper.StaffMapper.applyProfile;
import static vn.ducthe.organic.mapper.StaffMapper.view;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.StaffRequests.Create;
import vn.ducthe.organic.dto.request.StaffRequests.Profile;
import vn.ducthe.organic.dto.response.StaffResponses.Staff;
import vn.ducthe.organic.entity.AccountEntity;
import vn.ducthe.organic.entity.AccountRoleEntity;
import vn.ducthe.organic.entity.BaseEntity;
import vn.ducthe.organic.entity.EmployeeEntity;
import vn.ducthe.organic.entity.RoleEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.repository.OrderTransactions;
import vn.ducthe.organic.service.StaffService;
import vn.ducthe.organic.service.impl.RoleAccess;

@Service
public class StaffServiceImpl implements StaffService {
    private static final String GUARDS = "staff_administration";
    private static final String ACTIVE = AuthStore.ACTIVE;
    private final MongoDocumentRepository mongo;
    private final RoleAccess roles;
    private final PasswordEncoder passwords;
    private final OrderTransactions transactions;

    public StaffServiceImpl(MongoDocumentRepository mongo, RoleAccess roles, PasswordEncoder passwords, OrderTransactions transactions) {
        this.mongo = mongo;
        this.roles = roles;
        this.passwords = passwords;
        this.transactions = transactions;
    }

    @Override
    public PageData<Staff> list(int page, int size) {
        var pageable = PageRequest.of(page - 1, size, Sort.by("employeeCode", "_id"));
        long total = mongo.count(new Query(), EmployeeEntity.class);
        List<EmployeeEntity> employees = mongo.find(new Query().with(pageable), EmployeeEntity.class);
        List<String> accountIds = employees.stream().map(EmployeeEntity::getAccountId).toList();
        Map<String, AccountEntity> accounts = mongo.find(Query.query(Criteria.where("_id").in(accountIds)),
                AccountEntity.class).stream().collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        Map<String, List<RoleEntity.Code>> assignments = roles.roles(accountIds);
        List<Staff> items = employees.stream().map(employee -> view(employee, accounts.get(employee.getAccountId()),
                assignments.getOrDefault(employee.getAccountId(), List.of()))).toList();
        return PageData.from(new PageImpl<>(items, pageable, total));
    }

    @Override
    public Staff detail(String id) {
        EmployeeEntity employee = employee(id);
        return view(employee, account(employee), roles.roles(employee.getAccountId()));
    }

    @Override
    public Staff create(String actor, Create input) {
        String passwordHash = hash(input.password());
        return change(actor, () -> create(input.username(), passwordHash, input.profile(), input.role().code(), actor));
    }

    @Override
    public Staff update(String actor, String id, Profile profile) {
        return change(actor, () -> {
            EmployeeEntity employee = employee(id);
            applyProfile(employee, profile);
            employee.setUpdatedAt(Instant.now());
            mongo.save(employee);
            return view(employee, account(employee), roles.roles(employee.getAccountId()));
        });
    }

    @Override
    public Staff status(String actor, String id, StaffStatus status) {
        return change(actor, () -> {
            EmployeeEntity employee = employee(id);
            AccountEntity account = account(employee);
            rejectSelf(actor, account);
            if (!status.name().equals(account.getStatus())) {
                account.setStatus(status.name());
                revoke(account);
                mongo.save(account);
            }
            return view(employee, account, roles.roles(account.getId()));
        });
    }

    @Override
    public Staff role(String actor, String id, StaffRole requested) {
        return change(actor, () -> {
            EmployeeEntity employee = employee(id);
            AccountEntity account = account(employee);
            rejectSelf(actor, account);
            if (!roles.roles(account.getId()).equals(List.of(requested.code()))) {
                RoleEntity target = activeRole(requested.code());
                Instant now = Instant.now();
                mongo.updateMulti(Query.query(Criteria.where("accountId").is(account.getId())
                                .and("roleId").ne(target.getId()).and("status").is(ACTIVE)),
                        new Update().set("status", "INACTIVE").set("updatedAt", now), AccountRoleEntity.class);
                assign(account.getId(), target.getId(), actor, now);
                revoke(account);
                mongo.save(account);
            }
            return view(employee, account, roles.roles(account.getId()));
        });
    }

    @Override
    public void resetPassword(String actor, String id, String password) {
        String passwordHash = hash(password);
        change(actor, () -> {
            AccountEntity account = account(employee(id));
            rejectSelf(actor, account);
            account.setPasswordHash(passwordHash);
            account.setPasswordChangedAt(nextCredentialTime(account));
            account.setUpdatedAt(Instant.now());
            mongo.save(account);
            return null;
        });
    }

    @Override
    public boolean bootstrap(String username, String password, Profile profile) {
        ensureGuard();
        return transactions.execute(() -> {
            lockGuard();
            RoleEntity admin = mongo.findOne(by("code", RoleEntity.Code.ADMIN), RoleEntity.class);
            if (admin != null && mongo.exists(Query.query(Criteria.where("roleId").is(admin.getId())),
                    AccountRoleEntity.class)) {
                return false;
            }
            if (username == null || !username.matches("[A-Za-z0-9_]{3,32}") || password == null
                    || password.length() < 12 || password.isBlank() || profile.fullName().isBlank()
                    || profile.fullName().length() > 100) {
                throw new IllegalArgumentException("Invalid initial administrator configuration");
            }
            create(username, hash(password), profile, RoleEntity.Code.ADMIN, null);
            return true;
        });
    }

    private Staff create(String username, String passwordHash, Profile profile, RoleEntity.Code roleCode, String actor) {
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        if (mongo.exists(by("username", normalized), AccountEntity.class)) {
            throw new ApiException(ApiError.CONFLICT);
        }
        Instant now = Instant.now();
        RoleEntity role = activeRole(roleCode);

        AccountEntity account = new AccountEntity();
        account.setUsername(normalized);
        account.setPasswordHash(passwordHash);
        account.setPasswordChangedAt(now);
        account.setStatus(ACTIVE);
        stamp(account, now);
        mongo.insert(account);

        EmployeeEntity employee = new EmployeeEntity();
        employee.setAccountId(account.getId());
        employee.setEmployeeCode("NV-" + account.getId());
        employee.setWorkStatus(ACTIVE);
        stamp(employee, now);
        applyProfile(employee, profile);
        mongo.insert(employee);

        assign(account.getId(), role.getId(), actor, now);
        return view(employee, account, List.of(roleCode));
    }

    private RoleEntity activeRole(RoleEntity.Code code) {
        RoleEntity role = mongo.findOne(by("code", code), RoleEntity.class);
        if (role == null) {
            Instant now = Instant.now();
            role = new RoleEntity();
            role.setCode(code);
            role.setName(code.name());
            role.setStatus(ACTIVE);
            stamp(role, now);
            return mongo.insert(role);
        }
        if (!ACTIVE.equals(role.getStatus())) {
            throw new ApiException(ApiError.STAFF_ROLE_INACTIVE);
        }
        return role;
    }

    private void assign(String accountId, String roleId, String actor, Instant now) {
        Query filter = Query.query(Criteria.where("accountId").is(accountId).and("roleId").is(roleId));
        mongo.upsert(filter, new Update().setOnInsert("createdAt", now).set("status", ACTIVE)
                .set("assignedAt", now).set("assignedByAccountId", actor).set("updatedAt", now), AccountRoleEntity.class);
    }

    private <T> T change(String actor, Supplier<T> operation) {
        ensureGuard();
        return transactions.execute(() -> {
            lockGuard();
            if (!roles.roles(actor).contains(RoleEntity.Code.ADMIN) || !mongo.exists(
                    Query.query(Criteria.where("_id").is(actor).and("status").is(ACTIVE)), AccountEntity.class)) {
                throw new ApiException(ApiError.ACCESS_DENIED);
            }
            return operation.get();
        });
    }

    private void ensureGuard() {
        try {
            mongo.upsert(by("_id", "roles"), new Update().setOnInsert("revision", 0), Document.class, GUARDS);
        } catch (DuplicateKeyException exception) {
            if (!mongo.exists(by("_id", "roles"), GUARDS)) {
                throw exception;
            }
        }
    }

    private void lockGuard() {
        // Serialize administrator mutations so two administrators cannot revoke each other concurrently.
        mongo.updateFirst(by("_id", "roles"), new Update().inc("revision", 1), Document.class, GUARDS);
    }

    private EmployeeEntity employee(String id) {
        EmployeeEntity employee = mongo.findById(id, EmployeeEntity.class);
        if (employee == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return employee;
    }

    private AccountEntity account(EmployeeEntity employee) {
        AccountEntity account = mongo.findById(employee.getAccountId(), AccountEntity.class);
        if (account == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return account;
    }

    private String hash(String value) {
        if (value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(ApiError.VALIDATION_ERROR);
        }
        return passwords.encode(value);
    }

    private static void rejectSelf(String actor, AccountEntity account) {
        if (actor.equals(account.getId())) {
            throw new ApiException(ApiError.STAFF_SELF_CHANGE);
        }
    }

    private static void revoke(AccountEntity account) {
        account.setSessionsRevokedAt(nextCredentialTime(account));
        account.setUpdatedAt(Instant.now());
    }

    private static Instant nextCredentialTime(AccountEntity account) {
        return Instant.ofEpochMilli(Math.max(System.currentTimeMillis(),
                Long.parseLong(AuthStore.credentialVersion(account)) + 1));
    }

    private static void stamp(BaseEntity entity, Instant now) {
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
    }

    private static Query by(String key, Object value) {
        return Query.query(Criteria.where(key).is(value));
    }
}
