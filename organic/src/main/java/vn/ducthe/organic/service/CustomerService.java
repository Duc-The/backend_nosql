package vn.ducthe.organic.service;

import java.util.List;
import vn.ducthe.organic.dto.request.CustomerRequests.AddressInput;
import vn.ducthe.organic.dto.request.CustomerRequests.ProfileInput;
import vn.ducthe.organic.dto.response.CustomerResponses.Address;
import vn.ducthe.organic.dto.response.CustomerResponses.Profile;

public interface CustomerService {

    Profile profile(String accountId, String username);

    Profile updateProfile(String accountId, String username, ProfileInput input);

    List<Address> addresses(String accountId);

    Address add(String accountId, AddressInput input);

    Address update(String accountId, String id, AddressInput input);

    Address setDefault(String accountId, String id);

    void delete(String accountId, String id);
}
