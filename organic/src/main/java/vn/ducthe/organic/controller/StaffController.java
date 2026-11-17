package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.annotation.CurrentJwt;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.common.response.PageData;
import vn.ducthe.organic.dto.request.StaffRequests;
import vn.ducthe.organic.dto.request.StaffRequests.PasswordInput;
import vn.ducthe.organic.dto.request.StaffRequests.RoleInput;
import vn.ducthe.organic.dto.request.StaffRequests.StatusInput;
import vn.ducthe.organic.dto.response.StaffResponses.RoleOption;
import vn.ducthe.organic.dto.response.StaffResponses.Staff;
import vn.ducthe.organic.entity.RoleEntity;
import vn.ducthe.organic.service.StaffService;

@RestController
@RequestMapping("/api/v1/admin")
public class StaffController {
    private final StaffService staff;

    public StaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping("/roles")
    ResponseEntity<ApiResponse<List<RoleOption>>> roles(HttpServletRequest request) {
        List<RoleOption> options = Arrays.stream(RoleEntity.Code.values())
                .map(code -> new RoleOption(code, switch (code) {
                    case ADMIN -> "Chủ cửa hàng";
                    case SALES_STAFF -> "Nhân viên bán hàng";
                    case WAREHOUSE_STAFF -> "Nhân viên kho";
                    case CUSTOMER -> "Khách hàng";
                }, code != RoleEntity.Code.CUSTOMER)).toList();
        return ApiResponse.ok(options, "Lấy danh sách vai trò thành công", request);
    }

    @GetMapping("/employees")
    ResponseEntity<ApiResponse<PageData<Staff>>> list(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.ok(staff.list(page, size), "Lấy danh sách nhân viên thành công", request);
    }

    @GetMapping("/employees/{id}")
    ResponseEntity<ApiResponse<Staff>> detail(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(staff.detail(id), "Lấy thông tin nhân viên thành công", request);
    }

    @PostMapping("/employees")
    ResponseEntity<ApiResponse<Staff>> create(@CurrentJwt Jwt jwt,
            @Valid @RequestBody StaffRequests.Create body, HttpServletRequest request) {
        return ApiResponse.created(staff.create(jwt.getSubject(), body), "Đã tạo tài khoản nhân viên", request);
    }

    @PutMapping("/employees/{id}")
    ResponseEntity<ApiResponse<Staff>> update(@CurrentJwt Jwt jwt, @PathVariable String id,
            @Valid @RequestBody StaffRequests.Profile body, HttpServletRequest request) {
        return ApiResponse.ok(staff.update(jwt.getSubject(), id, body), "Đã cập nhật hồ sơ nhân viên", request);
    }

    @PutMapping("/employees/{id}/status")
    ResponseEntity<ApiResponse<Staff>> status(@CurrentJwt Jwt jwt, @PathVariable String id,
            @Valid @RequestBody StatusInput body, HttpServletRequest request) {
        return ApiResponse.ok(staff.status(jwt.getSubject(), id, body.status()), "Đã cập nhật trạng thái tài khoản", request);
    }

    @PutMapping("/employees/{id}/role")
    ResponseEntity<ApiResponse<Staff>> role(@CurrentJwt Jwt jwt, @PathVariable String id,
            @Valid @RequestBody RoleInput body, HttpServletRequest request) {
        return ApiResponse.ok(staff.role(jwt.getSubject(), id, body.role()), "Đã cập nhật vai trò", request);
    }

    @PutMapping("/employees/{id}/password")
    ResponseEntity<ApiResponse<Void>> password(@CurrentJwt Jwt jwt, @PathVariable String id,
            @Valid @RequestBody PasswordInput body, HttpServletRequest request) {
        staff.resetPassword(jwt.getSubject(), id, body.newPassword());
        return ApiResponse.ok(null, "Đã đặt lại mật khẩu nhân viên", request);
    }
}
