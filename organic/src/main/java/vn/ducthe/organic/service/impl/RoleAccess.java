package vn.ducthe.organic.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import vn.ducthe.organic.entity.AccountRoleEntity;
import vn.ducthe.organic.entity.RoleEntity;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.MongoDocumentRepository;

@Component
public class RoleAccess {
    private final MongoDocumentRepository mongo;

    public RoleAccess(MongoDocumentRepository mongo) {
        this.mongo = mongo;
    }

    public List<RoleEntity.Code> roles(String accountId) {
        return roles(List.of(accountId)).getOrDefault(accountId, List.of());
    }

    public Map<String, List<RoleEntity.Code>> roles(Collection<String> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        List<AccountRoleEntity> assignments = mongo.find(Query.query(Criteria.where("accountId").in(accountIds)
                .and("status").is(AuthStore.ACTIVE)), AccountRoleEntity.class);
        if (assignments.isEmpty()) {
            return Map.of();
        }
        var roleIds = assignments.stream().map(AccountRoleEntity::getRoleId).distinct().toList();
        Map<String, RoleEntity> roles = mongo.find(Query.query(Criteria.where("_id").in(roleIds)
                        .and("status").is(AuthStore.ACTIVE)), RoleEntity.class).stream()
                .collect(Collectors.toMap(RoleEntity::getId, Function.identity()));
        return assignments.stream().collect(Collectors.groupingBy(AccountRoleEntity::getAccountId,
                Collectors.mapping(assignment -> roles.get(assignment.getRoleId()), Collectors.collectingAndThen(
                        Collectors.toList(), matches -> matches.stream().filter(Objects::nonNull)
                                .map(RoleEntity::getCode).distinct().sorted().toList()))));
    }
}
