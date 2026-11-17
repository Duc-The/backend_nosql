package vn.ducthe.organic.service;

import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.StaffRequests.Create;
import vn.ducthe.organic.dto.request.StaffRequests.Profile;
import vn.ducthe.organic.dto.response.StaffResponses.Staff;
import vn.ducthe.organic.entity.RoleEntity;

public interface StaffService {

    PageData<Staff> list(int page, int size);

    Staff detail(String id);

    Staff create(String actor, Create input);

    Staff update(String actor, String id, Profile profile);

    Staff status(String actor, String id, StaffStatus status);

    Staff role(String actor, String id, StaffRole requested);

    void resetPassword(String actor, String id, String password);

    boolean bootstrap(String username, String password, Profile profile);

    enum StaffRole {
        ADMIN, SALES_STAFF, WAREHOUSE_STAFF;

        public RoleEntity.Code code() {
            return RoleEntity.Code.valueOf(name());
        }
    }

    enum StaffStatus { ACTIVE, LOCKED }
}
