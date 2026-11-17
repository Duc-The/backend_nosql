package vn.ducthe.organic.service.impl;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.ReceivingPayloads.SupplierInput;
import vn.ducthe.organic.dto.request.ReceivingPayloads.SupplierStatus;
import vn.ducthe.organic.entity.SupplierEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.service.SupplierService;

@Service
public class SupplierServiceImpl implements SupplierService {
    private final MongoDocumentRepository mongo;

    public SupplierServiceImpl(MongoDocumentRepository mongo) {
        this.mongo = mongo;
    }

    @Override
    public PageData<SupplierEntity> list(String text, SupplierStatus status, int page, int size) {
        Query filter = new Query();
        if (status != null) {
            filter.addCriteria(Criteria.where("status").is(status.name()));
        }
        if (text != null && !text.isBlank()) {
            Pattern search = Pattern.compile(Pattern.quote(text.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            filter.addCriteria(new Criteria().orOperator(Criteria.where("name").regex(search),
                    Criteria.where("supplierCode").regex(search), Criteria.where("taxCode").regex(search)));
        }
        var pageable = PageRequest.of(page - 1, size, Sort.by("name", "_id"));
        long total = mongo.count(filter, SupplierEntity.class);
        var rows = mongo.find(Query.of(filter).with(pageable), SupplierEntity.class);
        return PageData.from(new PageImpl<>(rows, pageable, total));
    }

    @Override
    public SupplierEntity get(String id) {
        return required(mongo.findById(id, SupplierEntity.class));
    }

    @Override
    public SupplierEntity active(String id) {
        SupplierEntity supplier = get(id);
        if (!AuthStore.ACTIVE.equals(supplier.getStatus())) {
            throw new ApiException(ApiError.SUPPLIER_INACTIVE);
        }
        return supplier;
    }

    @Override
    public SupplierEntity create(SupplierInput input) {
        SupplierEntity row = new SupplierEntity();
        row.setSupplierCode(input.supplierCode().toUpperCase(Locale.ROOT));
        row.setName(input.name().strip());
        row.setTaxCode(clean(input.taxCode()));
        row.setContactPerson(clean(input.contactPerson()));
        row.setPhoneNumber(clean(input.phoneNumber()));
        row.setEmail(clean(input.email()));
        row.setAddress(clean(input.address()));
        row.setNote(clean(input.note()));
        row.setStatus(AuthStore.ACTIVE);
        Instant now = Instant.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return mongo.insert(row);
    }

    @Override
    public SupplierEntity edit(String id, SupplierInput input) {
        return update(id, new Update().set("supplierCode", input.supplierCode().toUpperCase(Locale.ROOT))
                .set("name", input.name().strip()).set("taxCode", clean(input.taxCode()))
                .set("contactPerson", clean(input.contactPerson())).set("phoneNumber", clean(input.phoneNumber()))
                .set("email", clean(input.email())).set("address", clean(input.address())).set("note", clean(input.note())));
    }

    @Override
    public SupplierEntity status(String id, SupplierStatus status) {
        return update(id, new Update().set("status", status.name()));
    }

    private SupplierEntity update(String id, Update change) {
        return required(mongo.findAndModify(Query.query(Criteria.where("_id").is(id)),
                change.set("updatedAt", Instant.now()), FindAndModifyOptions.options().returnNew(true), SupplierEntity.class));
    }

    private static SupplierEntity required(SupplierEntity row) {
        if (row == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
