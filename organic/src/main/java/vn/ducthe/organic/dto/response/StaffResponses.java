package vn.ducthe.organic.dto.response;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.dto.response.StaffResponses.Staff;
import vn.ducthe.organic.entity.RoleEntity;

public final class StaffResponses {
    private StaffResponses() {}

    public record RoleOption(RoleEntity.Code code, String name, boolean staffAssignable) {}

    public record Staff(String id, String employeeCode, String accountId, String username, String accountStatus,
                        String fullName, String email, String phoneNumber, String jobTitle, String workStatus,
                        LocalDate hiredOn, List<RoleEntity.Code> roles) {}
}
