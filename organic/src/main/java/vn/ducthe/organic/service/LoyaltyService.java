package vn.ducthe.organic.service;

import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.LoyaltyResponses.Balance;
import vn.ducthe.organic.dto.response.LoyaltyResponses.PointEntry;
import vn.ducthe.organic.dto.response.LoyaltyResponses.Voucher;

public interface LoyaltyService {

    Balance balance(String accountId);

    PageData<PointEntry> history(String accountId, int page, int size);

    PageData<Voucher> vouchers(String accountId, int page, int size);

    Redemption redeem(String accountId, String key);

    void awardCompletedOrders();

    record Redemption(Voucher voucher, boolean created) {}
}
