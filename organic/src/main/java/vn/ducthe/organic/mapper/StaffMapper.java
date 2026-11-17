package vn.ducthe.organic.mapper;

import java.util.List;
import vn.ducthe.organic.dto.request.StaffRequests.Profile;
import vn.ducthe.organic.dto.response.StaffResponses.Staff;
import vn.ducthe.organic.entity.AccountEntity;
import vn.ducthe.organic.entity.EmployeeEntity;
import vn.ducthe.organic.entity.RoleEntity;

public final class StaffMapper {
    private StaffMapper() {}

    public static void applyProfile(EmployeeEntity employee, Profile profile) {
        employee.setFullName(profile.fullName().strip());
        employee.setEmail(clean(profile.email()));
        employee.setPhoneNumber(clean(profile.phoneNumber()));
        employee.setJobTitle(clean(profile.jobTitle()));
        employee.setHiredOn(profile.hiredOn());
    }

    public static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public static Staff view(EmployeeEntity employee, AccountEntity account, List<RoleEntity.Code> roles) {
        return new Staff(employee.getId(), employee.getEmployeeCode(), employee.getAccountId(),
                account == null ? null : account.getUsername(), account == null ? null : account.getStatus(),
                employee.getFullName(), employee.getEmail(), employee.getPhoneNumber(), employee.getJobTitle(),
                employee.getWorkStatus(), employee.getHiredOn(), roles);
    }
}
