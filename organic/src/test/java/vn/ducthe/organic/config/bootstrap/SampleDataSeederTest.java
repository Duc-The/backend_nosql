package vn.ducthe.organic.config.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.ducthe.organic.entity.*;

@SpringBootTest(properties = {"app.seed.enabled=true", "app.staff.bootstrap.enabled=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SampleDataSeederTest {
    private static final String DATABASE = "organic_seed_test_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://127.0.0.1:27017/" + DATABASE);
    }

    @Autowired MongoTemplate mongo;
    @Autowired SampleDataSeeder seeder;
    @Autowired vn.ducthe.organic.service.ProductReviewService reviews;

    @AfterAll
    void cleanup() {
        assertEquals(DATABASE, mongo.getDb().getName());
        mongo.getDb().drop();
    }

    @Test
    void seedsUsableAccountsConsistentLoyaltyAndDoesNotResetExistingData() {
        assertEquals(6, mongo.count(new Query(), ProductEntity.class));
        assertEquals(12, mongo.count(new Query(), ProductVariantEntity.class));
        assertEquals(24, mongo.count(new Query(), OrderEntity.class));
        assertEquals(3, mongo.count(new Query(), CustomerVoucherEntity.class));
        assertEquals(12, mongo.count(new Query(), CustomerPointTransactionEntity.class));
        for (String username : new String[]{"admin", "warehouse", "sales"}) {
            AccountEntity account = mongo.findOne(Query.query(Criteria.where("username").is(username)), AccountEntity.class);
            assertNotNull(account);
            assertEquals("ACTIVE", account.getStatus());
            assertTrue(new BCryptPasswordEncoder().matches("Organic@123456", account.getPasswordHash()));
            var assignment = mongo.findOne(Query.query(Criteria.where("accountId").is(account.getId())), AccountRoleEntity.class);
            assertNotNull(assignment);
            var role = mongo.findById(assignment.getRoleId(), RoleEntity.class);
            assertNotNull(role);
            assertEquals(switch (username) {
                case "admin" -> RoleEntity.Code.ADMIN;
                case "warehouse" -> RoleEntity.Code.WAREHOUSE_STAFF;
                default -> RoleEntity.Code.SALES_STAFF;
            }, role.getCode());
        }
        for (var customer : mongo.findAll(CustomerEntity.class)) {
            long points = mongo.find(Query.query(Criteria.where("customerId").is(customer.getId())),
                    CustomerPointTransactionEntity.class).stream().mapToLong(CustomerPointTransactionEntity::getPointsDelta).sum();
            assertTrue(points > 0);
            assertEquals(points, customer.getRewardPoints());
        }
        var admin = mongo.findOne(Query.query(Criteria.where("username").is("admin")), AccountEntity.class);
        String changedHash = new BCryptPasswordEncoder().encode("Changed@123456");
        mongo.updateFirst(Query.query(Criteria.where("_id").is(admin.getId())),
                new Update().set("passwordHash", changedHash), AccountEntity.class);
        var batch = mongo.findOne(new Query(), ProductBatchEntity.class);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(batch.getId())),
                new Update().set("status", "INACTIVE"), ProductBatchEntity.class);
        var before = counts();
        var balances = mongo.findAll(CustomerEntity.class).stream().collect(Collectors.toMap(CustomerEntity::getId, CustomerEntity::getRewardPoints));
        seeder.run(new DefaultApplicationArguments());
        assertEquals(before, counts());
        assertEquals(changedHash, mongo.findById(admin.getId(), AccountEntity.class).getPasswordHash());
        assertEquals("INACTIVE", mongo.findById(batch.getId(), ProductBatchEntity.class).getStatus());
        assertEquals(balances, mongo.findAll(CustomerEntity.class).stream().collect(Collectors.toMap(CustomerEntity::getId, CustomerEntity::getRewardPoints)));
    }

    @Test
    void opensReviewOnlyForCustomerWhoCompletedAndPaidForThisProduct() {
        String account = SeedNames.PREFIX + "account-customer-1";
        String otherAccount = SeedNames.PREFIX + "account-customer-2";
        var eligible = reviews.eligibility(account, "cai-bo-xoi");
        assertTrue(eligible.canReview());
        assertEquals("MX-DH-1", eligible.orderCode());
        assertFalse(reviews.eligibility(otherAccount, "cai-bo-xoi").canReview());
        assertThrows(vn.ducthe.organic.exception.ApiException.class,
                () -> reviews.form(otherAccount, eligible.orderCode(), eligible.productId()));
        assertThrows(vn.ducthe.organic.exception.ApiException.class,
                () -> reviews.form(account, "MX-DH-4", eligible.productId()));
        try {
            reviews.create(account, eligible.orderCode(), eligible.productId(),
                    new vn.ducthe.organic.dto.request.ProductReviewRequest(5, "Sản phẩm tươi ngon"),
                    java.util.List.of(), null);
            var after = reviews.eligibility(account, "cai-bo-xoi");
            assertTrue(after.alreadyReviewed());
            assertFalse(after.canReview());
            assertEquals(1, reviews.feed("cai-bo-xoi", 1, 8).total());
        } finally {
            mongo.remove(new Query(), ProductReviewEntity.class);
        }
    }

    private Map<String, Long> counts() {
        return mongo.getCollectionNames().stream().collect(Collectors.toMap(name -> name,
                name -> mongo.getCollection(name).countDocuments()));
    }
}
