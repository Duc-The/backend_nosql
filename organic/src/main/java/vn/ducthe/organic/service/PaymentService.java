package vn.ducthe.organic.service;

import java.math.BigDecimal;
import java.util.Map;
import vn.ducthe.organic.dto.response.PaymentResponses.PaymentView;
import vn.ducthe.organic.entity.PaymentEntity.Method;

public interface PaymentService {

    void requireConfigured(Method method);

    void requireOnlineAmount(Method method, BigDecimal amount);

    PaymentView get(String accountId, String orderCode);

    PaymentView start(String accountId, String orderCode, String ipAddress);

    PaymentView confirmCod(String accountId, String orderCode);

    String verifyVnpayReturn(String accountId, String orderCode, Map<String, String> fields);

    CallbackResult vnpay(Map<String, String> fields);

    CallbackResult payos(Map<String, Object> payload);

    enum CallbackResult { ACCEPTED, ALREADY_CONFIRMED, INVALID_SIGNATURE, INVALID_AMOUNT, INVALID_DATA, NOT_FOUND }
}
