package vn.ducthe.organic.integration.payment;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import vn.ducthe.organic.entity.PaymentEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;

@Component
public class PaymentGateways {
    private static final String SHA_256 = "HmacSHA256";
    private static final String SHA_512 = "HmacSHA512";
    private static final String PAYOS_PATH = "/v2/payment-requests";
    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE = new ParameterizedTypeReference<>() {};

    private final String vnpCode;
    private final String vnpSecret;
    private final String vnpUrl;
    private final String vnpReturn;
    private final String payosSecret;
    private final String payosReturn;
    private final String payosCancel;
    private final boolean payosConfigured;
    private final RestClient payos;

    PaymentGateways(@Value("${app.payment.vnpay.tmn-code:}") String vnpCode,
                    @Value("${app.payment.vnpay.hash-secret:}") String vnpSecret,
                    @Value("${app.payment.vnpay.payment-url:}") String vnpUrl,
                    @Value("${app.payment.vnpay.return-url:}") String vnpReturn,
                    @Value("${app.payment.payos.client-id:}") String payosId,
                    @Value("${app.payment.payos.api-key:}") String payosKey,
                    @Value("${app.payment.payos.checksum-key:}") String payosSecret,
                    @Value("${app.payment.payos.return-url:}") String payosReturn,
                    @Value("${app.payment.payos.cancel-url:}") String payosCancel) {
        this.vnpCode = vnpCode;
        this.vnpSecret = vnpSecret;
        this.vnpUrl = vnpUrl;
        this.vnpReturn = vnpReturn;
        this.payosSecret = payosSecret;
        this.payosReturn = payosReturn;
        this.payosCancel = payosCancel;
        payosConfigured = !payosId.isBlank() && !payosKey.isBlank() && !payosSecret.isBlank()
                && !payosReturn.isBlank() && !payosCancel.isBlank();
        var httpClient = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        payos = RestClient.builder().baseUrl("https://api-merchant.payos.vn").requestFactory(factory)
                .defaultHeader("x-client-id", payosId).defaultHeader("x-api-key", payosKey).build();
    }

    public void requireConfigured(PaymentEntity.Method method) {
        boolean configured = switch (method) {
            case COD -> true;
            case VNPAY -> !vnpCode.isBlank() && !vnpSecret.isBlank() && !vnpUrl.isBlank() && !vnpReturn.isBlank();
            case VIETQR -> payosConfigured;
        };
        if (!configured) {
            throw new ApiException(ApiError.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }

    public Link vnpayLink(PaymentEntity payment, String orderCode, String ipAddress) {
        requireConfigured(PaymentEntity.Method.VNPAY);
        Map<String, String> fields = new TreeMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", vnpCode);
        fields.put("vnp_Amount", payment.getAmount().movePointRight(2).toBigIntegerExact().toString());
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", payment.getMerchantReference());
        fields.put("vnp_OrderInfo", "Thanh toan " + payment.getMerchantReference());
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", returnUrl(vnpReturn, orderCode));
        fields.put("vnp_IpAddr", ipAddress);
        fields.put("vnp_CreateDate", VNP_DATE.format(payment.getCreatedAt()));
        fields.put("vnp_ExpireDate", VNP_DATE.format(payment.getExpiresAt()));
        String data = vnpData(fields);
        String separator = vnpUrl.contains("?") ? "&" : "?";
        return new Link(vnpUrl + separator + data + "&vnp_SecureHash="
                + GatewaySignature.sign(SHA_512, vnpSecret, data), null, null);
    }

    public boolean validVnpay(Map<String, String> fields) {
        if (vnpCode.isBlank() || vnpSecret.isBlank() || !vnpCode.equals(fields.get("vnp_TmnCode"))) {
            return false;
        }
        Map<String, String> signed = new TreeMap<>(fields);
        signed.remove("vnp_SecureHash");
        signed.remove("vnp_SecureHashType");
        return GatewaySignature.matches(GatewaySignature.sign(SHA_512, vnpSecret, vnpData(signed)),
                fields.get("vnp_SecureHash"));
    }

    public Link payosLink(PaymentEntity payment, String orderCode) {
        requireConfigured(PaymentEntity.Method.VIETQR);
        Map<String, Object> body = new TreeMap<>();
        body.put("orderCode", Long.parseLong(payment.getMerchantReference()));
        body.put("amount", payment.getAmount().longValueExact());
        body.put("description", "DH" + payment.getMerchantReference());
        body.put("returnUrl", returnUrl(payosReturn, orderCode));
        body.put("cancelUrl", returnUrl(payosCancel, orderCode));
        body.put("signature", GatewaySignature.sign(SHA_256, payosSecret, payosData(body)));
        body.put("expiredAt", payment.getExpiresAt().getEpochSecond());
        try {
            Map<String, Object> response = payos.post().uri(PAYOS_PATH).body(body).retrieve().body(RESPONSE);
            Map<?, ?> data = responseData(response);
            if (validLink(data, payment) && validPayos(data, string(response, "signature"))) {
                String id = string(data, "paymentLinkId");
                String url = string(data, "checkoutUrl");
                if (isLinkId(id) && url != null && url.startsWith("https://pay.payos.vn/")) {
                    return new Link(url, string(data, "qrCode"), id);
                }
            }
        } catch (RestClientException exception) {
            // The provider may have created the link even if the response was lost.
        }
        return recoverPayosLink(payment);
    }

    public boolean validPayos(Map<?, ?> data, String signature) {
        if (payosSecret.isBlank() || data == null || data.isEmpty()) {
            return false;
        }
        try {
            return GatewaySignature.matches(GatewaySignature.sign(SHA_256, payosSecret, payosData(data)), signature);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private Link recoverPayosLink(PaymentEntity payment) {
        try {
            Map<?, ?> data = responseData(payos.get().uri(PAYOS_PATH + "/{id}", payment.getMerchantReference())
                    .retrieve().body(RESPONSE));
            if (validLink(data, payment)) {
                String id = string(data, "id");
                if (isLinkId(id)) {
                    return new Link("https://pay.payos.vn/web/" + id, null, id);
                }
            }
        } catch (RestClientException exception) {
            // Let the client retry the same stable merchant reference.
        }
        throw new ApiException(ApiError.PAYMENT_GATEWAY_UNAVAILABLE);
    }

    private static Map<?, ?> responseData(Map<String, Object> response) {
        return response != null && "00".equals(response.get("code")) && response.get("data") instanceof Map<?, ?> data
                ? data : Map.of();
    }

    private static boolean validLink(Map<?, ?> data, PaymentEntity payment) {
        if (!payment.getMerchantReference().equals(string(data, "orderCode"))
                || !"PENDING".equals(string(data, "status"))) {
            return false;
        }
        try {
            return payment.getAmount().compareTo(new BigDecimal(string(data, "amount"))) == 0;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }

    private static boolean isLinkId(String id) {
        return id != null && id.matches("[a-zA-Z0-9_-]{8,128}");
    }

    public static String string(Map<?, ?> data, String field) {
        Object value = data.get(field);
        return value instanceof String || value instanceof Number ? value.toString() : null;
    }

    private static String vnpData(Map<String, String> sorted) {
        return sorted.entrySet().stream().filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String payosData(Map<?, ?> fields) {
        Map<String, String> sorted = new TreeMap<>();
        fields.forEach((key, value) -> {
            if (!(key instanceof String) || value != null && !(value instanceof String)
                    && !(value instanceof Number) && !(value instanceof Boolean)) {
                throw new IllegalArgumentException("Unsupported payOS signature field");
            }
            String text = value == null ? "" : value.toString();
            sorted.put((String) key, "null".equals(text) || "undefined".equals(text) ? "" : text);
        });
        return sorted.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private static String returnUrl(String base, String orderCode) {
        return UriComponentsBuilder.fromUriString(base).queryParam("merchantOrderCode", orderCode).build().toUriString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    public record Link(String url, String qr, String providerLinkId) {}
}
