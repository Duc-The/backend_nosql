package vn.ducthe.organic.service.impl;

import static vn.ducthe.organic.mapper.CustomerMapper.address;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import vn.ducthe.organic.dto.request.CustomerRequests.AddressInput;
import vn.ducthe.organic.dto.request.CustomerRequests.ProfileInput;
import vn.ducthe.organic.dto.response.CustomerResponses.Address;
import vn.ducthe.organic.dto.response.CustomerResponses.Profile;
import vn.ducthe.organic.entity.CustomerAddressEntity;
import vn.ducthe.organic.entity.CustomerEntity;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;
import vn.ducthe.organic.mapper.CustomerMapper;
import vn.ducthe.organic.repository.AuthStore;
import vn.ducthe.organic.repository.CustomerRepository;
import vn.ducthe.organic.service.CustomerService;

@Service
public class CustomerServiceImpl implements CustomerService {
    private final AuthStore auth;
    private final CustomerRepository customers;

    public CustomerServiceImpl(AuthStore auth, CustomerRepository customers) {
        this.auth = auth;
        this.customers = customers;
    }

    @Override
    public Profile profile(String accountId, String username) {
        CustomerEntity customer = auth.customer(accountId);
        return new Profile(customer.getId(), username, customer.getEmail(), customer.getFullName(),
                customer.getPhoneNumber(), customer.getDateOfBirth());
    }

    @Override
    public Profile updateProfile(String accountId, String username, ProfileInput input) {
        CustomerEntity customer = auth.customer(accountId);
        String fullName = input.fullName().strip();
        String phone = input.phoneNumber();
        LocalDate birthDate = input.dateOfBirth();
        customers.updateProfile(customer.getId(), fullName, phone, birthDate);
        return new Profile(customer.getId(), username, customer.getEmail(), fullName, phone, birthDate);
    }

    @Override
    public synchronized List<Address> addresses(String accountId) {
        String customerId = auth.customer(accountId).getId();
        ensureDefault(customerId);
        return customers.addresses(customerId).stream()
                .map(CustomerMapper::address).toList();
    }

    @Override
    public synchronized Address add(String accountId, AddressInput input) {
        String customerId = auth.customer(accountId).getId();
        CustomerAddressEntity entity = new CustomerAddressEntity();
        entity.setCustomerId(customerId);
        entity.setStatus(AuthStore.ACTIVE);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        CustomerMapper.applyAddress(entity, input);
        customers.insertAddress(entity);
        ensureDefault(customerId);
        return address(findAddress(customerId, entity.getId()));
    }

    @Override
    public synchronized Address update(String accountId, String id, AddressInput input) {
        String customerId = auth.customer(accountId).getId();
        CustomerAddressEntity entity = new CustomerAddressEntity();
        CustomerMapper.applyAddress(entity, input);
        if (!customers.updateAddress(customerId, id, entity)) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return address(findAddress(customerId, id));
    }

    @Override
    public synchronized Address setDefault(String accountId, String id) {
        String customerId = auth.customer(accountId).getId();
        CustomerAddressEntity selected = findAddress(customerId, id);
        if (!selected.isDefaultAddress()) {
            customers.clearDefaultAddress(customerId);
            customers.setDefaultAddress(customerId, id);
        }
        return address(findAddress(customerId, id));
    }

    @Override
    public synchronized void delete(String accountId, String id) {
        String customerId = auth.customer(accountId).getId();
        if (!customers.deleteAddress(customerId, id)) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        ensureDefault(customerId);
    }

    private void ensureDefault(String customerId) {
        if (customers.hasDefaultAddress(customerId)) {
            return;
        }
        CustomerAddressEntity newest = customers.newestAddress(customerId);
        if (newest != null) {
            customers.setDefaultAddress(customerId, newest.getId());
        }
    }

    private CustomerAddressEntity findAddress(String customerId, String id) {
        CustomerAddressEntity entity = customers.address(customerId, id);
        if (entity == null) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        return entity;
    }
}
