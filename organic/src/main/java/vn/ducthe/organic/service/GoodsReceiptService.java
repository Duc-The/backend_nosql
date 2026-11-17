package vn.ducthe.organic.service;

import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.ReceivingPayloads.ReceiptInput;
import vn.ducthe.organic.dto.request.ReceivingPayloads.ReceiptStatus;
import vn.ducthe.organic.dto.response.GoodsReceiptResponses.Detail;
import vn.ducthe.organic.dto.response.GoodsReceiptResponses.ReceivingVariant;
import vn.ducthe.organic.dto.response.GoodsReceiptResponses.Summary;

public interface GoodsReceiptService {

    PageData<Summary> list(String supplierId, ReceiptStatus status, int page, int size);

    Detail detail(String receiptId);

    PageData<ReceivingVariant> variants(String q, int page, int size);

    Detail create(String accountId, ReceiptInput input);

    Detail edit(String accountId, String receiptId, ReceiptInput input);

    Detail confirm(String accountId, String receiptId, String note);

    Detail cancel(String accountId, String receiptId, String reason);
}
