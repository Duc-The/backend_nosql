package vn.ducthe.organic.service.impl;

import org.springframework.stereotype.Service;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.response.AdminCustomerResponses.CustomerRow;
import vn.ducthe.organic.mapper.CustomerMapper;
import vn.ducthe.organic.repository.CustomerRepository;
import vn.ducthe.organic.service.AdminCustomerService;

@Service
public class AdminCustomerServiceImpl implements AdminCustomerService {
    private final CustomerRepository customers;

    public AdminCustomerServiceImpl(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public PageData<CustomerRow> list(String q, boolean activeOnly, int page, int size) {
        return PageData.from(customers.search(q, activeOnly, page, size).map(CustomerMapper::adminRow));
    }
}
