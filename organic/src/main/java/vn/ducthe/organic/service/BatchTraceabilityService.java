package vn.ducthe.organic.service;

import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.BatchTraceabilityResponses.IssuedOrder;
import vn.ducthe.organic.dto.response.BatchTraceabilityResponses.TracedBatch;

public interface BatchTraceabilityService {

    PageData<TracedBatch> lookup(String rawCode, int page, int size);

    PageData<IssuedOrder> issuedOrders(String batchId, int page, int size);
}
