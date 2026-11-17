package vn.ducthe.organic.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import vn.ducthe.organic.entity.CustomerAddressEntity;
import vn.ducthe.organic.entity.CustomerEntity;

@Repository
public class CustomerRepository {
    private final MongoTemplate mongo;

    public CustomerRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Page<CustomerEntity> search(String q, boolean activeOnly, int page, int size) {
        Query filter = new Query();
        if (activeOnly) {
            filter.addCriteria(Criteria.where("status").is(AuthStore.ACTIVE));
        }
        if (q != null && !q.isBlank()) {
            Pattern match = Pattern.compile(Pattern.quote(q.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            filter.addCriteria(new Criteria().orOperator(Criteria.where("customerCode").regex(match),
                    Criteria.where("fullName").regex(match), Criteria.where("email").regex(match)));
        }
        var pageable = PageRequest.of(page - 1, size, Sort.by("fullName", "_id"));
        long total = mongo.count(filter, CustomerEntity.class);
        return new PageImpl<>(mongo.find(Query.of(filter).with(pageable), CustomerEntity.class), pageable, total);
    }

    public void updateProfile(String id, String fullName, String phone, LocalDate birthDate) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),
                new Update().set("fullName", fullName).set("phoneNumber", phone)
                        .set("dateOfBirth", birthDate).set("updatedAt", Instant.now()), CustomerEntity.class);
    }

    public List<CustomerAddressEntity> addresses(String customerId) {
        return mongo.find(ordered(customerId), CustomerAddressEntity.class);
    }

    public CustomerAddressEntity address(String customerId, String id) {
        return mongo.findOne(owned(customerId, id), CustomerAddressEntity.class);
    }

    public CustomerAddressEntity newestAddress(String customerId) {
        return mongo.findOne(ordered(customerId), CustomerAddressEntity.class);
    }

    public void insertAddress(CustomerAddressEntity entity) {
        mongo.insert(entity);
    }

    public boolean updateAddress(String customerId, String id, CustomerAddressEntity entity) {
        Update update = new Update().set("label", entity.getLabel())
                .set("receiverName", entity.getReceiverName()).set("receiverPhone", entity.getReceiverPhone())
                .set("province", entity.getProvince()).set("district", entity.getDistrict())
                .set("ward", entity.getWard()).set("streetAddress", entity.getStreetAddress())
                .set("updatedAt", Instant.now());
        return mongo.updateFirst(owned(customerId, id), update, CustomerAddressEntity.class).getMatchedCount() > 0;
    }

    public boolean deleteAddress(String customerId, String id) {
        return mongo.remove(owned(customerId, id), CustomerAddressEntity.class).getDeletedCount() > 0;
    }

    public boolean hasDefaultAddress(String customerId) {
        return mongo.exists(defaultQuery(customerId), CustomerAddressEntity.class);
    }

    public void clearDefaultAddress(String customerId) {
        mongo.updateFirst(defaultQuery(customerId), defaultUpdate(false), CustomerAddressEntity.class);
    }

    public void setDefaultAddress(String customerId, String id) {
        mongo.updateFirst(owned(customerId, id), defaultUpdate(true), CustomerAddressEntity.class);
    }

    private static Criteria active(String customerId) {
        return Criteria.where("customerId").is(customerId).and("status").is(AuthStore.ACTIVE);
    }

    private static Query owned(String customerId, String id) {
        return Query.query(active(customerId).and("_id").is(id));
    }

    private static Query ordered(String customerId) {
        return Query.query(active(customerId)).with(Sort.by(Sort.Direction.DESC, "createdAt", "_id"));
    }

    private static Query defaultQuery(String customerId) {
        return Query.query(active(customerId).and("defaultAddress").is(true));
    }

    private static Update defaultUpdate(boolean value) {
        return new Update().set("defaultAddress", value).set("updatedAt", Instant.now());
    }
}
