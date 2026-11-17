package vn.ducthe.organic.config.bootstrap;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class SeedDataMigration {
    private static final Logger log = LoggerFactory.getLogger(SeedDataMigration.class);
    private static final String MARKER = "catalog-naming-v1";
    private static final String CART_PREFIX = "organic:cart:v1:";
    private static final String OLD_WELCOME = "DEMO-ORGANIC10";
    private static final Pattern LEGACY_ID = Pattern.compile("^" + SeedNames.LEGACY_PREFIX);
    private static final Set<String> COLLECTIONS = Set.of("accounts", "account_roles", "account_identities", "roles",
            "employees", "customers", "customer_addresses", "customer_point_transactions", "customer_vouchers",
            "categories", "suppliers", "products", "product_variants", "product_batches", "goods_receipts",
            "goods_receipt_details", "orders", "order_details", "order_detail_batches", "payments", "payment_events",
            "promotions", "promotion_products", "promotion_batches", "order_promotions");
    private static final Set<String> CODES = Set.of("categoryCode", "customerCode", "employeeCode", "supplierCode",
            "productCode", "sku", "batchCode", "lotCode", "receiptCode", "orderCode", "paymentCode");
    private static final DefaultRedisScript<Long> MOVE_FIELD = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], ARGV[1]) ~= ARGV[2] then return 0 end
            if ARGV[1] == ARGV[3] then
                redis.call('HSET', KEYS[1], ARGV[3], ARGV[4])
            else
                redis.call('HSETNX', KEYS[1], ARGV[3], ARGV[4])
                redis.call('HDEL', KEYS[1], ARGV[1])
            end
            return 1
            """, Long.class);

    private final MongoTemplate mongo;
    private final StringRedisTemplate redis;

    SeedDataMigration(MongoTemplate mongo, StringRedisTemplate redis) {
        this.mongo = mongo;
        this.redis = redis;
    }

    void migrate() {
        MongoCollection<Document> journal = mongo.getCollection("data_migrations");
        Set<String> present = Set.copyOf(mongo.getCollectionNames());
        if (journal.find(Filters.eq("_id", MARKER)).first() == null) {
            boolean legacyExists = COLLECTIONS.stream().filter(present::contains).anyMatch(name ->
                    mongo.getCollection(name).find(Filters.regex("_id", LEGACY_ID))
                            .projection(new Document("_id", 1)).first() != null);
            if (!legacyExists) {
                return;
            }
            journal.insertOne(new Document("_id", MARKER));
        }

        // Persist the original before replacing an immutable MongoDB ID so a restart can resume.
        for (Document pending : journal.find(Filters.exists("collection")).into(new ArrayList<>())) {
            move(journal, pending);
        }
        for (String name : COLLECTIONS) {
            if (!present.contains(name)) {
                continue;
            }
            MongoCollection<Document> collection = mongo.getCollection(name);
            for (Document original : collection.find(Filters.regex("_id", LEGACY_ID)).into(new ArrayList<>())) {
                Document pending = new Document("_id", name + ":" + original.getString("_id"))
                        .append("collection", name).append("original", original);
                journal.insertOne(pending);
                move(journal, pending);
            }
        }

        // Also update snapshots and references in records created after the initial catalog import.
        for (String name : COLLECTIONS) {
            if (!present.contains(name)) {
                continue;
            }
            MongoCollection<Document> collection = mongo.getCollection(name);
            try (var cursor = collection.find().iterator()) {
                while (cursor.hasNext()) {
                    Document row = cursor.next();
                    Document changed = new Document();
                    row.forEach((key, value) -> {
                        if (!"_id".equals(key)) {
                            Object normalized = normalize(key, value, false);
                            if (!java.util.Objects.equals(value, normalized)) {
                                changed.put(key, normalized);
                            }
                        }
                    });
                    if (!changed.isEmpty()) {
                        collection.updateOne(Filters.eq("_id", row.get("_id")), new Document("$set", changed));
                    }
                }
            }
        }
        migrateCarts();
        journal.deleteOne(Filters.eq("_id", MARKER));
        log.info("Catalog identifiers and references updated");
    }

    private void move(MongoCollection<Document> journal, Document pending) {
        MongoCollection<Document> collection = mongo.getCollection(pending.getString("collection"));
        Document original = pending.get("original", Document.class);
        String oldId = original.getString("_id");
        Document replacement = (Document) normalize("", original, true);
        String newId = replacement.getString("_id");
        if (collection.find(Filters.eq("_id", newId)).first() != null) {
            if (collection.find(Filters.eq("_id", oldId)).first() != null) {
                throw new IllegalStateException("Catalog identifier collision in " + pending.getString("collection"));
            }
        } else {
            collection.deleteOne(Filters.eq("_id", oldId));
            try {
                collection.insertOne(replacement);
            } catch (RuntimeException exception) {
                if (collection.find(Filters.eq("_id", newId)).first() == null) {
                    collection.insertOne(original);
                    throw exception;
                }
            }
        }
        journal.deleteOne(Filters.eq("_id", pending.get("_id")));
    }

    private void migrateCarts() {
        var hashes = redis.<String, String>opsForHash();
        try (var cursor = redis.scan(ScanOptions.scanOptions().match(CART_PREFIX + "*").count(100).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String customerId = key.substring(CART_PREFIX.length());
                String target = CART_PREFIX + SeedNames.identifier(customerId);
                if (!target.equals(key)) {
                    if (!Boolean.TRUE.equals(redis.hasKey(key))) {
                        continue;
                    }
                    if (!Boolean.TRUE.equals(redis.renameIfAbsent(key, target))) {
                        Map<String, String> previous = hashes.entries(key);
                        previous.forEach((field, value) -> hashes.putIfAbsent(target, field, value));
                        redis.delete(key);
                    }
                }
                Map<String, String> fields = new LinkedHashMap<>(hashes.entries(target));
                for (var entry : fields.entrySet()) {
                    String field = entry.getKey();
                    String value = entry.getValue();
                    String newField = SeedNames.identifier(field);
                    String newValue = "@address".equals(field) ? SeedNames.identifier(value)
                            : "@promotion".equals(field) && OLD_WELCOME.equalsIgnoreCase(value)
                            ? SeedNames.WELCOME_CODE : value;
                    if (!field.equals(newField) || !value.equals(newValue)) {
                        redis.execute(MOVE_FIELD, List.of(target), field, value, newField, newValue);
                    }
                }
            }
        }
    }

    private static Object normalize(String field, Object value, boolean legacyOwned) {
        if (value instanceof Document document) {
            Document converted = new Document();
            document.forEach((key, item) -> converted.put(key, normalize(key, item, legacyOwned)));
            return converted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> normalize(field, item, legacyOwned)).toList();
        }
        if (!(value instanceof String text)) {
            return value;
        }
        if (("_id".equals(field) || field.endsWith("Id")) && text.startsWith(SeedNames.LEGACY_PREFIX)) {
            return SeedNames.identifier(text);
        }
        if ("idempotencyKey".equals(field) && text.startsWith("earn:" + SeedNames.LEGACY_PREFIX)) {
            return "earn:" + SeedNames.identifier(text.substring("earn:".length()));
        }
        if ("promotionCode".equals(field) && OLD_WELCOME.equals(text)) {
            return SeedNames.WELCOME_CODE;
        }
        if (legacyOwned && CODES.contains(field) || "sku".equals(field) && text.matches("DEMO-SP-[0-9]+-[0-9]+G")
                || "eventKey".equals(field) && text.matches("DEMO-TT-[0-9]+")) {
            return SeedNames.operationalCode(text);
        }
        if (legacyOwned && "slug".equals(field) && text.startsWith("demo-")) {
            return text.substring("demo-".length());
        }
        if (legacyOwned && "username".equals(field) && text.matches("demo-(warehouse|sales|customer-([1-9]|[1-5][0-9]|60))")) {
            return SeedNames.username(text.substring("demo-".length()));
        }
        if (legacyOwned && "email".equals(field)
                && text.matches("(warehouse|sales|customer-([1-9]|[1-5][0-9]|60))@example\\.test")) {
            return SeedNames.email(text.substring(0, text.indexOf('@')));
        }
        if (legacyOwned && "email".equals(field)
                && text.matches("(rau|trai-cay|gao|dau-hat|tra|gia-vi)@example\\.test")) {
            return SeedNames.supplierEmail(text.substring(0, text.indexOf('@')));
        }
        if ("streetAddress".equals(field) && text.endsWith(", khu dân cư mẫu Mùa Xanh")) {
            return text.replace("khu dân cư mẫu Mùa Xanh", "Khu dân cư Mùa Xanh");
        }
        if (legacyOwned && "note".equals(field) && "Nhà cung cấp giả lập phục vụ phát triển.".equals(text)) {
            return "Đối tác cung ứng theo mùa.";
        }
        if ("reason".equals(field) && text.startsWith("Tích điểm đơn DEMO-DH-")) {
            return SeedNames.operationalCode(text);
        }
        return text;
    }
}
