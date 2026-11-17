package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.annotation.CurrentJwt;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.dto.request.CustomerRequests.AddressInput;
import vn.ducthe.organic.dto.request.CustomerRequests.ProfileInput;
import vn.ducthe.organic.dto.response.CustomerResponses.Address;
import vn.ducthe.organic.dto.response.CustomerResponses.Profile;
import vn.ducthe.organic.service.CustomerService;

@RestController
@RequestMapping("/api/v1/customers/me")
public class CustomerController {
    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    ResponseEntity<ApiResponse<Profile>> profile(@CurrentJwt Jwt jwt, HttpServletRequest request) {
        return ApiResponse.ok(customers.profile(jwt.getSubject(), jwt.getClaimAsString("username")),
                "Lấy hồ sơ thành công", request);
    }

    @PutMapping
    ResponseEntity<ApiResponse<Profile>> updateProfile(@CurrentJwt Jwt jwt,
            @Valid @RequestBody ProfileInput body, HttpServletRequest request) {
        return ApiResponse.ok(customers.updateProfile(jwt.getSubject(), jwt.getClaimAsString("username"), body),
                "Đã cập nhật hồ sơ", request);
    }

    @GetMapping("/addresses")
    ResponseEntity<ApiResponse<List<Address>>> addresses(@CurrentJwt Jwt jwt, HttpServletRequest request) {
        return ApiResponse.ok(customers.addresses(jwt.getSubject()), "Lấy sổ địa chỉ thành công", request);
    }

    @PostMapping("/addresses")
    ResponseEntity<ApiResponse<Address>> add(@CurrentJwt Jwt jwt, @Valid @RequestBody AddressInput body,
            HttpServletRequest request) {
        return ApiResponse.created(customers.add(jwt.getSubject(), body), "Đã thêm địa chỉ", request);
    }

    @PutMapping("/addresses/{id}")
    ResponseEntity<ApiResponse<Address>> update(@CurrentJwt Jwt jwt, @PathVariable String id,
            @Valid @RequestBody AddressInput body, HttpServletRequest request) {
        return ApiResponse.ok(customers.update(jwt.getSubject(), id, body), "Đã cập nhật địa chỉ", request);
    }

    @PutMapping("/addresses/{id}/default")
    ResponseEntity<ApiResponse<Address>> setDefault(@CurrentJwt Jwt jwt, @PathVariable String id,
            HttpServletRequest request) {
        return ApiResponse.ok(customers.setDefault(jwt.getSubject(), id), "Đã chọn địa chỉ mặc định", request);
    }

    @DeleteMapping("/addresses/{id}")
    ResponseEntity<Void> delete(@CurrentJwt Jwt jwt, @PathVariable String id) {
        customers.delete(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }
}
