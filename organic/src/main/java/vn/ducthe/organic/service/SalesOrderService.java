package vn.ducthe.organic.service;

import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.SalesOrderResponses.Detail;
import vn.ducthe.organic.dto.response.SalesOrderResponses.Row;

public interface SalesOrderService {

    PageData<Row> list(String accountId, Status status, String assignee, String q, int page, int size);

    Detail detail(String accountId, String orderCode);

    Detail assign(String accountId, String orderCode, String employeeId);

    Detail transition(String accountId, String orderCode, Status target, String carrierName, String trackingCode);

    enum Status { PENDING, CONFIRMED, PACKING, PACKED, SHIPPING, DELIVERED, COMPLETED, CANCELLED }
}
