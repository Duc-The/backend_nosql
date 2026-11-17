package vn.ducthe.organic.repository;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.Collection;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.stereotype.Repository;

@Repository
public class MongoDocumentRepository {
    private final MongoTemplate mongo;

    public MongoDocumentRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public <T> T readDocument(Class<T> type, Document document) {
        return mongo.getConverter().read(type, document);
    }

    public Object toMongoValue(Object value) {
        return mongo.getConverter().convertToMongoType(value);
    }

    public <T> List<T> find(Query query, Class<T> type) {
        return mongo.find(query, type);
    }

    public <T> T findOne(Query query, Class<T> type) {
        return mongo.findOne(query, type);
    }

    public <T> T findById(Object id, Class<T> type) {
        return mongo.findById(id, type);
    }

    public long count(Query query, Class<?> type) {
        return mongo.count(query, type);
    }

    public boolean exists(Query query, String collection) {
        return mongo.exists(query, collection);
    }

    public boolean exists(Query query, Class<?> type) {
        return mongo.exists(query, type);
    }

    public <T> T insert(T entity) {
        return mongo.insert(entity);
    }

    public <T> Collection<T> insert(Collection<? extends T> entities, Class<?> type) {
        return mongo.insert(entities, type);
    }

    public <T> T save(T entity) {
        return mongo.save(entity);
    }

    public DeleteResult remove(Query query, Class<?> type) {
        return mongo.remove(query, type);
    }

    public UpdateResult updateFirst(Query query, UpdateDefinition update, Class<?> type) {
        return mongo.updateFirst(query, update, type);
    }

    public UpdateResult updateFirst(Query query, UpdateDefinition update, Class<?> type, String collection) {
        return mongo.updateFirst(query, update, type, collection);
    }

    public UpdateResult updateMulti(Query query, UpdateDefinition update, Class<?> type) {
        return mongo.updateMulti(query, update, type);
    }

    public UpdateResult upsert(Query query, UpdateDefinition update, Class<?> type) {
        return mongo.upsert(query, update, type);
    }

    public UpdateResult upsert(Query query, UpdateDefinition update, Class<?> type, String collection) {
        return mongo.upsert(query, update, type, collection);
    }

    public <T> T findAndModify(Query query, UpdateDefinition update, FindAndModifyOptions options, Class<T> type) {
        return mongo.findAndModify(query, update, options, type);
    }

    public BulkOperations bulkOps(BulkOperations.BulkMode mode, Class<?> type) {
        return mongo.bulkOps(mode, type);
    }

    public <T> AggregationResults<T> aggregate(Aggregation query, Class<?> inputType, Class<T> resultType) {
        return mongo.aggregate(query, inputType, resultType);
    }

    public <T> AggregationResults<T> aggregate(Aggregation query, String collection, Class<T> resultType) {
        return mongo.aggregate(query, collection, resultType);
    }

    public String getCollectionName(Class<?> type) {
        return mongo.getCollectionName(type);
    }
}
