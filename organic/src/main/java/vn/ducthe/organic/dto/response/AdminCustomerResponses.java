package vn.ducthe.organic.dto.response;

public final class AdminCustomerResponses {
    private AdminCustomerResponses() {}

    public record CustomerRow(String id, String customerCode, String fullName, String email, String status, long rewardPoints) {}
}
