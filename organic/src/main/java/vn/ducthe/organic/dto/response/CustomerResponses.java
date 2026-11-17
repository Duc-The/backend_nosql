package vn.ducthe.organic.dto.response;

import java.time.LocalDate;

public final class CustomerResponses {
    private CustomerResponses() {}

    public record Profile(String id, String username, String email, String fullName, String phoneNumber,
                          LocalDate dateOfBirth) {}

    public record Address(String id, String label, String receiverName, String receiverPhone, String province,
                          String district, String ward, String streetAddress, boolean defaultAddress) {}
}
