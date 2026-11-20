package vn.ducthe.organic.config.bootstrap;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import vn.ducthe.organic.entity.AccountEntity;
import vn.ducthe.organic.entity.AccountRoleEntity;
import vn.ducthe.organic.entity.BaseEntity;
import vn.ducthe.organic.entity.CategoryEntity;
import vn.ducthe.organic.entity.CustomerAddressEntity;
import vn.ducthe.organic.entity.CustomerEntity;
import vn.ducthe.organic.entity.CustomerVoucherEntity;
import vn.ducthe.organic.service.LoyaltyService;
import vn.ducthe.organic.entity.EmployeeEntity;
import vn.ducthe.organic.entity.GoodsReceiptDetailEntity;
import vn.ducthe.organic.entity.GoodsReceiptEntity;
import vn.ducthe.organic.entity.OrderDetailBatchEntity;
import vn.ducthe.organic.entity.OrderDetailEntity;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.PaymentEntity;
import vn.ducthe.organic.entity.ProductBatchEntity;
import vn.ducthe.organic.entity.ProductEntity;
import vn.ducthe.organic.entity.ProductVariantEntity;
import vn.ducthe.organic.entity.PromotionEntity;
import vn.ducthe.organic.entity.RoleEntity;
import vn.ducthe.organic.entity.SupplierEntity;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SampleDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);
    private static final String ACTIVE = "ACTIVE";
    private static final int CUSTOMER_COUNT = 3;
    private static final String DEFAULT_PASSWORD = "Organic@123456";
    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String CATALOG = """
            rau|Rau củ quả|Lâm Đồng|7|Cải bó xôi:42000:Salad hoặc xào nhanh;Cà rốt baby:38000:Hấp hoặc nướng
            trai-cay|Trái cây|Đồng Nai|14|Bưởi da xanh:85000:Tách múi dùng tươi;Xoài cát:79000:Để chín tự nhiên rồi dùng
            gao|Gạo và ngũ cốc|Sóc Trăng|240|Gạo lứt đỏ:56000:Ngâm trước khi nấu cơm;Yến mạch cán dẹt:89000:Nấu cháo hoặc ngâm qua đêm
            """;
    private static final String[] SURNAMES = {"Nguyễn", "Trần", "Lê", "Phạm", "Võ", "Huỳnh", "Đặng", "Bùi", "Đỗ", "Phan"};
    private static final String[] GIVEN_NAMES = {"Minh Anh", "Gia Hân", "Thanh Tùng", "Khánh Linh", "Đức Huy", "Ngọc Mai"};

    private final MongoTemplate mongo;
    private final SeedDataMigration migration;
    private final LoyaltyService loyalty;

    public SampleDataSeeder(MongoTemplate mongo, SeedDataMigration migration, LoyaltyService loyalty) {
        this.mongo = mongo;
        this.migration = migration;
        this.loyalty = loyalty;
    }

    @Override
    public void run(ApplicationArguments args) {
        CategoryEntity root = new CategoryEntity();
        root.setCategoryCode("MX-ORGANIC");
        root.setName("Thực phẩm organic");
        root.setSlug("thuc-pham-organic");
        root.setStatus(ACTIVE);
        insert(root, "category-organic", Instant.now());
        // Reuse the original seed date when resuming an interrupted startup.
        Instant anchor = mongo.findById(id("category-organic"), CategoryEntity.class).getCreatedAt();

        seedPeople(anchor);
        seedWelcomePromotion(anchor);
        int productNumber = 0;
        for (String row : CATALOG.strip().split("\\R")) {
            String[] category = row.split("\\|");
            int shelfLife = Integer.parseInt(category[3]);
            seedCategory(category, anchor);
            for (String item : category[4].split(";")) {
                String[] spec = item.split(":");
                ProductEntity product = seedProduct(++productNumber, category, spec, anchor);
                for (int size = 0; size < 2; size++) {
                    ProductVariantEntity variant = seedVariant(product, Integer.parseInt(spec[1]), shelfLife, size, anchor);
                    seedStockAndOrders(product, variant, category, shelfLife, productNumber, size, anchor);
                }
            }
        }
        seedCustomerVouchers(anchor);
        loyalty.awardCompletedOrders();
        log.info("Catalog ready: {} products, {} variants, {} customers and {} COD orders",
                productNumber, productNumber * 2, CUSTOMER_COUNT, productNumber * 4);
    }

    @PostConstruct
    void migrateCatalogNames() {
        migration.migrate();
    }

    private void seedWelcomePromotion(Instant anchor) {
        PromotionEntity promotion = new PromotionEntity();
        promotion.setPromotionCode(SeedNames.WELCOME_CODE);
        promotion.setName("Ưu đãi Organic 10%");
        promotion.setDescription("Giảm 10% tiền hàng cho đơn từ 100.000đ, tối đa 30.000đ.");
        promotion.setAudience(PromotionEntity.Audience.ALL_CUSTOMERS);
        promotion.setTarget(PromotionEntity.Target.ORDER);
        promotion.setDiscountType(PromotionEntity.DiscountType.PERCENTAGE);
        promotion.setDiscountValue(amount(10));
        promotion.setMinOrderValue(amount(100000));
        promotion.setMaxDiscountAmount(amount(30000));
        promotion.setPerCustomerLimit(1);
        promotion.setStatus(ACTIVE);
        insert(promotion, "promotion-welcome", anchor);
    }

    private void seedCustomerVouchers(Instant anchor) {
        PromotionEntity promotion = new PromotionEntity();
        promotion.setPromotionCode("MUAXANH20K");
        promotion.setName("Voucher chào mừng giảm 20.000đ");
        promotion.setDescription("Giảm 20.000đ cho đơn từ 100.000đ, dành cho khách được cấp voucher.");
        promotion.setAudience(PromotionEntity.Audience.ASSIGNED_CUSTOMERS);
        promotion.setTarget(PromotionEntity.Target.ORDER);
        promotion.setDiscountType(PromotionEntity.DiscountType.FIXED_AMOUNT);
        promotion.setDiscountValue(amount(20000));
        promotion.setMinOrderValue(amount(100000));
        promotion.setStartsAt(anchor);
        promotion.setPerCustomerLimit(1);
        promotion.setStatus(ACTIVE);
        insert(promotion, "promotion-customer-welcome", anchor);

        for (int number = 1; number <= CUSTOMER_COUNT; number++) {
            CustomerVoucherEntity voucher = new CustomerVoucherEntity();
            voucher.setCustomerId(id("customer-" + number));
            voucher.setPromotionId(promotion.getId());
            voucher.setVoucherCode("MX-WELCOME-" + number);
            voucher.setStatus(ACTIVE);
            voucher.setUsageLimit(1);
            voucher.setIssuedAt(anchor);
            insert(voucher, "welcome-voucher-" + number, anchor);
        }
    }

    private void seedPeople(Instant anchor) {
        Instant registered = anchor.minus(90, ChronoUnit.DAYS);
        for (RoleEntity.Code code : RoleEntity.Code.values()) {
            RoleEntity role = new RoleEntity();
            role.setCode(code);
            role.setName(code.name());
            role.setStatus(ACTIVE);
            // Reuse existing roles seeded by application code or created by an administrator.
            if (mongo.findOne(Query.query(Criteria.where("code").is(code)), RoleEntity.class) == null) {
                insert(role, "role-" + code.name(), registered);
            }
        }

        String passwordHash = new BCryptPasswordEncoder().encode(DEFAULT_PASSWORD);
        seedEmployee("admin", "Phan Đức Thể", "Chủ cửa hàng", RoleEntity.Code.ADMIN, passwordHash, registered);
        seedEmployee("warehouse", "Trần Minh Phúc", "Nhân viên kho", RoleEntity.Code.WAREHOUSE_STAFF, passwordHash, registered);
        seedEmployee("sales", "Nguyễn Hoài An", "Nhân viên bán hàng", RoleEntity.Code.SALES_STAFF, passwordHash, registered);

        for (int number = 1; number <= CUSTOMER_COUNT; number++) {
            String key = "customer-" + number;
            String name = SURNAMES[(number - 1) % SURNAMES.length] + " "
                    + GIVEN_NAMES[(number - 1) / SURNAMES.length];
            CustomerEntity customer = new CustomerEntity();
            customer.setAccountId(seedAccount(key, RoleEntity.Code.CUSTOMER, passwordHash, registered));
            customer.setCustomerCode("MX-KH-" + number);
            customer.setFullName(name);
            customer.setPhoneNumber(phone(number));
            customer.setEmail(SeedNames.email(key));
            customer.setRegisteredAt(registered);
            customer.setStatus(ACTIVE);
            insert(customer, key, registered);

            CustomerAddressEntity address = new CustomerAddressEntity();
            address.setCustomerId(customer.getId());
            address.setLabel("Nhà riêng");
            OrderEntity.AddressSnapshot snapshot = address(number);
            address.setReceiverName(snapshot.receiverName());
            address.setReceiverPhone(snapshot.receiverPhone());
            address.setProvince(snapshot.province());
            address.setWard(snapshot.ward());
            address.setStreetAddress(snapshot.streetAddress());
            address.setDefaultAddress(true);
            address.setStatus(ACTIVE);
            insert(address, "address-" + number, registered);
        }
    }

    private void seedEmployee(String key, String name, String title, RoleEntity.Code role,
                              String passwordHash, Instant registered) {
        EmployeeEntity employee = new EmployeeEntity();
        employee.setAccountId(seedAccount(key, role, passwordHash, registered));
        employee.setEmployeeCode("MX-NV-" + key);
        employee.setFullName(name);
        employee.setEmail(SeedNames.email(key));
        employee.setJobTitle(title);
        employee.setWorkStatus(ACTIVE);
        employee.setHiredOn(registered.atZone(SHOP_ZONE).toLocalDate());
        insert(employee, "employee-" + key, registered);
    }

    private String seedAccount(String key, RoleEntity.Code code, String passwordHash, Instant registered) {
        AccountEntity account = new AccountEntity();
        account.setUsername(SeedNames.username(key));
        account.setPasswordHash(passwordHash);
        account.setPasswordChangedAt(registered);
        account.setStatus(ACTIVE);
        insert(account, "account-" + key, registered);

        RoleEntity role = mongo.findOne(Query.query(Criteria.where("code").is(code)), RoleEntity.class);
        AccountRoleEntity assignment = new AccountRoleEntity();
        assignment.setAccountId(account.getId());
        assignment.setRoleId(role.getId());
        assignment.setStatus(ACTIVE);
        assignment.setAssignedAt(registered);
        insert(assignment, "assignment-" + key, registered);
        return account.getId();
    }

    private void seedCategory(String[] category, Instant anchor) {
        CategoryEntity child = new CategoryEntity();
        child.setCategoryCode("MX-DM-" + category[0]);
        child.setName(category[1]);
        child.setSlug(category[0]);
        child.setParentCategoryId(id("category-organic"));
        child.setStatus(ACTIVE);
        insert(child, "category-" + category[0], anchor);

        SupplierEntity supplier = new SupplierEntity();
        supplier.setSupplierCode("MX-NCC-" + category[0]);
        supplier.setName("Hợp tác xã Mùa Xanh " + category[2]);
        supplier.setAddress(category[2] + ", Việt Nam");
        supplier.setEmail(SeedNames.supplierEmail(category[0]));
        supplier.setStatus(ACTIVE);
        supplier.setNote("Đối tác cung ứng theo mùa.");
        insert(supplier, "supplier-" + category[0], anchor);
    }

    private ProductEntity seedProduct(int number, String[] category, String[] spec, Instant anchor) {
        ProductEntity product = new ProductEntity();
        product.setProductCode("MX-SP-" + number);
        product.setName(spec[0]);
        product.setSlug(slug(spec[0]));
        product.setCategoryId(id("category-" + category[0]));
        product.setBrandName("Mùa Xanh");
        product.setOriginDescription(category[2] + ", Việt Nam");
        product.setDescription(spec[2] + ".");
        product.setStorageInstructions(Integer.parseInt(category[3]) <= 14
                ? "Bảo quản lạnh, giữ khô và dùng sớm sau khi mở."
                : "Bảo quản nơi khô ráo, tránh nắng và đóng kín sau khi mở.");
        product.setFeatured(number % 6 == 1);
        product.setFeaturedOrder(number);
        product.setStatus(ACTIVE);
        insert(product, "product-" + number, anchor);
        return product;
    }

    private ProductVariantEntity seedVariant(ProductEntity product, int price, int shelfLife, int size, Instant anchor) {
        boolean lightPackage = shelfLife >= 270;
        int grams = (lightPackage ? 100 : 500) * (size + 1);
        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setProductId(product.getId());
        variant.setSku(product.getProductCode() + "-" + grams + "G");
        variant.setName(grams + " g");
        variant.setNetQuantity(amount(grams));
        variant.setUnitCode("G");
        variant.setSellingPrice(amount(size == 0 ? price : price * 18L / 10));
        variant.setReorderLevel(amount(10));
        variant.setStatus(ACTIVE);
        insert(variant, "variant-" + variant.getSku(), anchor);
        return variant;
    }

    private void seedStockAndOrders(ProductEntity product, ProductVariantEntity variant, String[] category,
                                    int shelfLife, int number, int size, Instant anchor) {
        int sequence = (number - 1) * 2 + size;
        int issued = 1 + sequence % 3;
        int pending = 1 + sequence % 2;
        boolean cancelled = sequence % 5 == 0;
        int firstStock = sequence % 11 == 0 ? 7 : 24 + sequence % 37;
        Instant received = anchor.minus(3, ChronoUnit.DAYS);
        ProductBatchEntity first = seedReceipt(variant, category, shelfLife, "A", firstStock,
                issued, cancelled ? 0 : pending, received);
        seedReceipt(variant, category, shelfLife, "B", 30 + sequence % 43, 0, 0,
                anchor.minus(1, ChronoUnit.DAYS));
        seedOrder(product, variant, first, sequence * 2 + 1, issued, "COMPLETED", anchor);
        seedOrder(product, variant, first, sequence * 2 + 2, pending, cancelled ? "CANCELLED" : "PENDING", anchor);
    }

    private ProductBatchEntity seedReceipt(ProductVariantEntity variant, String[] category, int shelfLife,
                                           String lot, int stock, int issued, int reserved, Instant received) {
        String code = variant.getSku() + "-" + lot;
        LocalDate receivedDate = received.atZone(SHOP_ZONE).toLocalDate();
        ProductBatchEntity batch = new ProductBatchEntity();
        batch.setVariantId(variant.getId());
        batch.setBatchCode(code);
        batch.setLotCode("LOT-" + code);
        batch.setManufactureDate(receivedDate.minusDays(1));
        batch.setExpiryDate(batch.getManufactureDate().plusDays(shelfLife));
        batch.setOriginDescription(category[2] + ", Việt Nam");
        batch.setQuantityOnHand(amount(stock - issued));
        batch.setQuantityReserved(amount(reserved));
        batch.setStatus(ACTIVE);

        int rejected = lot.equals("A") ? 1 : 0;
        BigDecimal cost = amount(variant.getSellingPrice().longValueExact() * 65 / 100);
        GoodsReceiptEntity receipt = new GoodsReceiptEntity();
        receipt.setReceiptCode("PN-" + code);
        receipt.setSupplierId(id("supplier-" + category[0]));
        receipt.setReceivedByEmployeeId(id("employee-warehouse"));
        receipt.setConfirmedByEmployeeId(id("employee-warehouse"));
        receipt.setReceivedAt(received);
        receipt.setConfirmedAt(received.plus(20, ChronoUnit.MINUTES));
        receipt.setTotalCost(cost.multiply(amount(stock)));
        receipt.setStatus("CONFIRMED");
        insert(receipt, "receipt-" + code, received);
        insert(batch, "batch-" + code, received);

        GoodsReceiptDetailEntity detail = new GoodsReceiptDetailEntity();
        detail.setGoodsReceiptId(receipt.getId());
        detail.setBatchId(batch.getId());
        detail.setVariantId(variant.getId());
        detail.setQuantityReceived(amount(stock + rejected));
        detail.setQuantityRejected(amount(rejected));
        detail.setUnitCost(cost);
        detail.setLineTotal(receipt.getTotalCost());
        if (rejected > 0) {
            detail.setDiscrepancyReason("Bao bì bị móp khi nhận hàng");
        }
        insert(detail, "receipt-detail-" + code, received);
        return batch;
    }

    private void seedOrder(ProductEntity product, ProductVariantEntity variant, ProductBatchEntity batch,
                           int number, int units, String status, Instant anchor) {
        boolean completed = status.equals("COMPLETED");
        boolean cancelled = status.equals("CANCELLED");
        Instant ordered = completed ? anchor.minus(2, ChronoUnit.DAYS).plus(number, ChronoUnit.MINUTES)
                : anchor.minus(60L + number, ChronoUnit.SECONDS);
        PaymentEntity.Status paymentStatus = completed ? PaymentEntity.Status.PAID
                : cancelled ? PaymentEntity.Status.CANCELLED : PaymentEntity.Status.PENDING;
        int customer = (number - 1) % CUSTOMER_COUNT + 1;
        BigDecimal quantity = amount(units);
        BigDecimal subtotal = quantity.multiply(variant.getSellingPrice());
        OrderEntity order = new OrderEntity();
        order.setOrderCode("MX-DH-" + number);
        order.setCustomerId(id("customer-" + customer));
        order.setAssignedEmployeeId(id("employee-sales"));
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus.name());
        order.setPaymentMethod(PaymentEntity.Method.COD);
        order.setShippingAddress(address(customer));
        order.setSubtotalAmount(subtotal);
        order.setShippingFee(amount(25000));
        order.setPromotionDiscount(BigDecimal.ZERO);
        order.setTotalAmount(subtotal.add(order.getShippingFee()));
        order.setOrderedAt(ordered);
        if (completed) {
            order.setCompletedAt(ordered.plus(1, ChronoUnit.DAYS));
        } else if (cancelled) {
            order.setCancelledAt(ordered.plusSeconds(10));
            order.setCancellationReason("Khách đổi lịch nhận hàng");
        } else {
            order.setStockReservedUntil(ordered.plus(1, ChronoUnit.DAYS));
        }
        insert(order, "order-" + number, ordered);

        OrderDetailEntity detail = new OrderDetailEntity();
        detail.setOrderId(order.getId());
        detail.setProductId(product.getId());
        detail.setProductName(product.getName());
        detail.setVariantId(variant.getId());
        detail.setVariantName(variant.getName());
        detail.setSku(variant.getSku());
        detail.setUnitCode(variant.getUnitCode());
        detail.setQuantity(quantity);
        detail.setUnitPrice(variant.getSellingPrice());
        detail.setDiscountAmount(BigDecimal.ZERO);
        detail.setVatRate(BigDecimal.ZERO);
        detail.setLineTotal(subtotal);
        insert(detail, "order-detail-" + number, ordered);

        OrderDetailBatchEntity allocation = new OrderDetailBatchEntity();
        allocation.setOrderDetailId(detail.getId());
        allocation.setBatchId(batch.getId());
        allocation.setQuantity(quantity);
        allocation.setStatus(completed ? "ISSUED" : cancelled ? "RELEASED" : "RESERVED");
        allocation.setReservedAt(ordered);
        if (completed) {
            allocation.setIssuedAt(ordered.plus(2, ChronoUnit.HOURS));
        } else if (cancelled) {
            allocation.setReleasedAt(order.getCancelledAt());
        }
        insert(allocation, "allocation-" + number, ordered);

        PaymentEntity payment = new PaymentEntity();
        payment.setOrderId(order.getId());
        payment.setPaymentCode("MX-TT-" + number);
        payment.setMethod(PaymentEntity.Method.COD);
        payment.setStatus(paymentStatus);
        payment.setAmount(order.getTotalAmount());
        if (completed) {
            payment.setPaidAt(order.getCompletedAt());
            payment.setConfirmedByEmployeeId(id("employee-sales"));
        }
        insert(payment, "payment-" + number, ordered);
    }

    private void insert(BaseEntity entity, String key, Instant time) {
        entity.setId(id(key));
        entity.setCreatedAt(time);
        entity.setUpdatedAt(time);
        if (mongo.findById(entity.getId(), entity.getClass()) != null) {
            return;
        }
        try {
            mongo.insert(entity);
        } catch (DuplicateKeyException exception) {
            if (mongo.findById(entity.getId(), entity.getClass()) == null) {
                throw exception;
            }
        }
    }

    private static OrderEntity.AddressSnapshot address(int number) {
        String name = SURNAMES[(number - 1) % SURNAMES.length] + " "
                + GIVEN_NAMES[(number - 1) / SURNAMES.length];
        return new OrderEntity.AddressSnapshot(name, phone(number), "Thành phố Hồ Chí Minh",
                null, number % 2 == 0 ? "Phường Tân Định" : "Phường Bến Thành",
                "Căn " + (100 + number) + ", Khu dân cư Mùa Xanh");
    }

    private static String phone(int number) {
        return String.format(Locale.ROOT, "000%07d", number);
    }

    private static String id(String key) {
        return SeedNames.PREFIX + key;
    }

    private static BigDecimal amount(long value) {
        return BigDecimal.valueOf(value);
    }

    private static String slug(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replaceAll("[^a-z0-9]+", "-");
    }
}
