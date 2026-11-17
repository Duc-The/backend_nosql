package vn.ducthe.organic.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.dto.response.ReportingResponses.Daily;
import vn.ducthe.organic.dto.response.ReportingResponses.OrderCount;
import vn.ducthe.organic.dto.response.ReportingResponses.PaymentMethod;
import vn.ducthe.organic.dto.response.ReportingResponses.Sales;
import vn.ducthe.organic.dto.response.ReportingResponses.Sku;
import vn.ducthe.organic.dto.response.ReportingResponses.TopSkus;
import vn.ducthe.organic.entity.OrderDetailEntity;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.PaymentEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.repository.MongoDocumentRepository;
import vn.ducthe.organic.service.ReportingService;

@Service
public class ReportingServiceImpl implements ReportingService {
    private static final String CURRENCY = "VND";
    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate EARLIEST_DATE = LocalDate.of(1970, 1, 1);
    private static final Decimal128 ZERO = Decimal128.POSITIVE_ZERO;
    private final MongoDocumentRepository mongo;

    public ReportingServiceImpl(MongoDocumentRepository mongo) {
        this.mongo = mongo;
    }

    @Override
    public Sales sales(LocalDate from, LocalDate to) {
        Window window = window(from, to);
        Document payment = payments(window);
        Document total = documents(payment, "totals").stream().findFirst().orElseGet(Document::new);
        List<PaymentMethod> byMethod = documents(payment, "methods").stream().map(row ->
                new PaymentMethod(row.getString("_id"), number(row, "paidOrderCount"), money(row, "collectedAmount")))
                .toList();
        Map<String, Document> paidDays = documents(payment, "days").stream()
                .collect(Collectors.toMap(row -> row.getString("_id"), Function.identity()));
        List<Daily> daily = window.from().datesUntil(window.to().plusDays(1)).map(date -> {
            Document row = paidDays.get(date.toString());
            return new Daily(date, number(row, "paidOrderCount"), money(row, "collectedAmount"));
        }).toList();
        List<OrderCount> status = ordersByStatus(window);
        return new Sales(window.from(), window.to(), SHOP_ZONE.getId(), CURRENCY, money(total, "collectedAmount"),
                money(total, "netMerchandiseAmount"), money(total, "shippingFee"), money(total, "promotionDiscount"),
                number(total, "paidOrderCount"), status.stream().mapToLong(OrderCount::count).sum(), byMethod, daily, status);
    }

    @Override
    public TopSkus topSkus(LocalDate from, LocalDate to, int limit) {
        Window window = window(from, to);
        Document snapshot = new Document("sku", "$line.sku").append("productId", "$line.productId")
                .append("productName", "$line.productName").append("variantName", "$line.variantName");
        Document grouped = new Document("_id", "$line.variantId")
                .append("quantitySold", sum("$line.quantity"))
                .append("grossMerchandiseAmount", sum(new Document("$multiply",
                        List.of(nonNull("$line.quantity"), nonNull("$line.unitPrice")))))
                .append("latest", new Document("$first", snapshot));
        List<Document> rows = aggregate(PaymentEntity.class,
                stage("$match", paidIn(window)),
                stage("$sort", new Document("paidAt", -1).append("_id", -1)),
                stage("$lookup", new Document("from", mongo.getCollectionName(OrderDetailEntity.class))
                        .append("localField", "orderId").append("foreignField", "orderId").append("as", "line")),
                stage("$unwind", "$line"),
                stage("$match", new Document("line.variantId", new Document("$type", "string"))),
                stage("$group", grouped),
                stage("$sort", new Document("quantitySold", -1).append("grossMerchandiseAmount", -1).append("_id", 1)),
                stage("$limit", limit));
        List<Sku> items = rows.stream().map(row -> {
            Document names = row.get("latest", Document.class);
            return new Sku(row.getString("_id"), names.getString("sku"), names.getString("productId"),
                    names.getString("productName"), names.getString("variantName"), money(row, "quantitySold"),
                    money(row, "grossMerchandiseAmount"));
        }).toList();
        return new TopSkus(window.from(), window.to(), SHOP_ZONE.getId(), CURRENCY, items);
    }

    private Document payments(Window window) {
        Document orderRef = new Document("$convert", new Document("input", "$orderId").append("to", "objectId")
                .append("onError", "$orderId").append("onNull", "$orderId"));
        Document totals = paymentGroup(null)
                .append("netMerchandiseAmount", sum(new Document("$subtract",
                        List.of(nonNull("$order.subtotalAmount"), nonNull("$order.promotionDiscount")))))
                .append("shippingFee", sum(nonNull("$order.shippingFee")))
                .append("promotionDiscount", sum(nonNull("$order.promotionDiscount")));
        Document day = new Document("$dateToString", new Document("format", "%Y-%m-%d")
                .append("date", "$paidAt").append("timezone", SHOP_ZONE.getId()));
        Document facet = new Document("totals", List.of(new Document("$group", totals)))
                .append("methods", List.of(new Document("$group", paymentGroup("$method")),
                        new Document("$sort", new Document("_id", 1))))
                .append("days", List.of(new Document("$group", paymentGroup(day))));
        return aggregate(PaymentEntity.class,
                stage("$match", paidIn(window)),
                stage("$set", new Document("orderRef", orderRef)),
                stage("$lookup", new Document("from", mongo.getCollectionName(OrderEntity.class))
                        .append("localField", "orderRef").append("foreignField", "_id").append("as", "order")),
                stage("$unwind", new Document("path", "$order").append("preserveNullAndEmptyArrays", true)),
                stage("$facet", facet)).stream().findFirst().orElseGet(Document::new);
    }

    private List<OrderCount> ordersByStatus(Window window) {
        return aggregate(OrderEntity.class,
                stage("$match", new Document("orderedAt", dates(window))),
                stage("$group", new Document("_id", "$status").append("count", sum(1))),
                stage("$sort", new Document("_id", 1))).stream()
                .map(row -> new OrderCount(row.getString("_id"), number(row, "count"))).toList();
    }

    private List<Document> aggregate(Class<?> collection, AggregationOperation... operations) {
        Aggregation query = Aggregation.newAggregation(operations)
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build());
        return mongo.aggregate(query, mongo.getCollectionName(collection),
                Document.class).getMappedResults();
    }

    private static AggregationOperation stage(String operator, Object body) {
        return context -> new Document(operator, body);
    }

    private static Document paidIn(Window window) {
        return new Document("status", PaymentEntity.Status.PAID.name()).append("currency", CURRENCY)
                .append("paidAt", dates(window));
    }

    private static Document dates(Window window) {
        return new Document("$gte", Date.from(window.from().atStartOfDay(SHOP_ZONE).toInstant()))
                .append("$lt", Date.from(window.to().plusDays(1).atStartOfDay(SHOP_ZONE).toInstant()));
    }

    private static Window window(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(SHOP_ZONE);
        LocalDate end = to == null ? today : to;
        if (end.isBefore(EARLIEST_DATE) || end.isAfter(today)) {
            throw new ApiException(ApiError.REPORT_DATE_RANGE_INVALID);
        }
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (from == null && start.isBefore(EARLIEST_DATE)) {
            start = EARLIEST_DATE;
        }
        if (start.isBefore(EARLIEST_DATE) || start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= 366) {
            throw new ApiException(ApiError.REPORT_DATE_RANGE_INVALID);
        }
        return new Window(start, end);
    }

    private static Document paymentGroup(Object key) {
        return new Document("_id", key).append("paidOrderCount", sum(1)).append("collectedAmount", sum("$amount"));
    }

    private static Document nonNull(String field) {
        return new Document("$ifNull", List.of(field, ZERO));
    }

    private static Document sum(Object expression) {
        return new Document("$sum", expression);
    }

    private static List<Document> documents(Document row, String field) {
        return row.getList(field, Document.class, List.of());
    }

    private static long number(Document row, String field) {
        Number value = row == null ? null : row.get(field, Number.class);
        return value == null ? 0 : value.longValue();
    }

    private static BigDecimal money(Document row, String field) {
        Object value = row == null ? null : row.get(field);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof Decimal128 decimal ? decimal.bigDecimalValue() : new BigDecimal(value.toString());
    }

    private record Window(LocalDate from, LocalDate to) {}
}
