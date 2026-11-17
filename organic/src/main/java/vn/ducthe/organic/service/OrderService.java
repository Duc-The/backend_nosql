package vn.ducthe.organic.service;

import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.OrderResponses.Detail;
import vn.ducthe.organic.dto.response.OrderResponses.Summary;
import vn.ducthe.organic.dto.response.OrderResponses.WarehouseOrder;
import vn.ducthe.organic.dto.response.OrderResponses.WarehouseQueueItem;
import vn.ducthe.organic.entity.OrderEntity;
import vn.ducthe.organic.entity.PaymentEntity;

public interface OrderService {

    CheckoutResult checkout(String accountId, String key, String note, PaymentEntity.Method requestedMethod);

    PageData<Summary> list(String accountId, int page, int size);

    Detail detail(String accountId, String orderCode);

    Detail cancel(String accountId, String orderCode, String reason);

    void expireHolds();

    PageData<WarehouseQueueItem> warehouseQueue(int page, int size);

    WarehouseOrder warehouseOrder(String orderCode);

    WarehouseOrder issue(String orderCode, String accountId);

    OrderEntity findOrder(String orderCode);

    Detail detail(OrderEntity order);

    record CheckoutResult(Detail order, boolean created) {}
}
