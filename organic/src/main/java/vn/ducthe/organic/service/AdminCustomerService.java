package vn.ducthe.organic.service;

import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.AdminCustomerResponses.CustomerRow;

public interface AdminCustomerService {
    PageData<CustomerRow> list(String q, boolean activeOnly, int page, int size);
}
