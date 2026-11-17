package vn.ducthe.organic.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportingResponses {
    private ReportingResponses() {}

    public record Sales(LocalDate from, LocalDate to, String timezone, String currency, BigDecimal collectedAmount,
                 BigDecimal netMerchandiseAmount, BigDecimal shippingFee, BigDecimal promotionDiscount,
                 long paidOrderCount, long placedOrderCount, List<PaymentMethod> paymentsByMethod,
                 List<Daily> dailyRevenue, List<OrderCount> ordersByStatus) {}

    public record PaymentMethod(String method, long paidOrderCount, BigDecimal collectedAmount) {}

    public record Daily(LocalDate date, long paidOrderCount, BigDecimal collectedAmount) {}

    public record OrderCount(String status, long count) {}

    public record TopSkus(LocalDate from, LocalDate to, String timezone, String currency, List<Sku> items) {}

    public record Sku(String variantId, String sku, String productId, String productName, String variantName,
               BigDecimal quantitySold, BigDecimal grossMerchandiseAmount) {}
}
