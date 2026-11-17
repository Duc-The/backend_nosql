package vn.ducthe.organic.mapper;

import vn.ducthe.organic.dto.request.CustomerRequests.AddressInput;
import vn.ducthe.organic.dto.response.AdminCustomerResponses.CustomerRow;
import vn.ducthe.organic.dto.response.CustomerResponses.Address;
import vn.ducthe.organic.entity.CustomerAddressEntity;
import vn.ducthe.organic.entity.CustomerEntity;

public final class CustomerMapper {
    private CustomerMapper() {}

    public static Address address(CustomerAddressEntity entity) {
        return new Address(entity.getId(), entity.getLabel(), entity.getReceiverName(), entity.getReceiverPhone(),
                entity.getProvince(), entity.getDistrict(), entity.getWard(), entity.getStreetAddress(),
                entity.isDefaultAddress());
    }
    public static CustomerRow adminRow(CustomerEntity row) {
        return new CustomerRow(row.getId(), row.getCustomerCode(), row.getFullName(),
                row.getEmail(), row.getStatus(), row.getRewardPoints());
    }
    public static void applyAddress(CustomerAddressEntity entity, AddressInput input) {
        entity.setLabel(input.label().strip());
        entity.setReceiverName(input.receiverName().strip());
        entity.setReceiverPhone(input.receiverPhone());
        entity.setProvince(input.province().strip());
        entity.setDistrict(clean(input.district()));
        entity.setWard(input.ward().strip());
        entity.setStreetAddress(input.streetAddress().strip());
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
