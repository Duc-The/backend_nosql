package vn.ducthe.organic.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.ducthe.organic.annotation.CurrentJwt;
import vn.ducthe.organic.common.response.ApiResponse;
import vn.ducthe.organic.dto.request.AuthPayloads;
import vn.ducthe.organic.dto.response.AuthResponses;
import vn.ducthe.organic.dto.response.JWTResponses.Tokens;
import vn.ducthe.organic.service.AuthService;
import vn.ducthe.organic.service.JWTService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final JWTService jwt;

    public AuthController(AuthService auth, JWTService jwt) {
        this.auth = auth;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody AuthPayloads.Register body, HttpServletRequest request) {
        auth.register(body);
        return ApiResponse.created(null, "Đã tạo tài khoản. Vui lòng kiểm tra email để xác minh", request);
    }

    @PostMapping("/register/resend-otp")
    ResponseEntity<ApiResponse<Void>> resend(@Valid @RequestBody AuthPayloads.EmailRequest body, HttpServletRequest request) {
        auth.resend(body.email());
        return ApiResponse.ok(null, "Đã gửi mã xác minh", request);
    }

    @PostMapping("/register/verify-otp")
    ResponseEntity<ApiResponse<Void>> verify(@Valid @RequestBody AuthPayloads.OtpRequest body, HttpServletRequest request) {
        auth.verifyRegistration(body);
        return ApiResponse.ok(null, "Xác minh tài khoản thành công", request);
    }

    @PostMapping("/login")
    ResponseEntity<ApiResponse<Tokens>> login(@Valid @RequestBody AuthPayloads.Login body, HttpServletRequest request) {
        return ApiResponse.ok(auth.login(body), "Đăng nhập thành công", request);
    }

    @PostMapping("/google")
    ResponseEntity<ApiResponse<Tokens>> google(@Valid @RequestBody AuthPayloads.GoogleLogin body, HttpServletRequest request) {
        return ApiResponse.ok(auth.googleLogin(body.idToken()), "Đăng nhập Google thành công", request);
    }

    @PostMapping("/refresh")
    ResponseEntity<ApiResponse<Tokens>> refresh(@Valid @RequestBody AuthPayloads.Refresh body, HttpServletRequest request) {
        return ApiResponse.ok(jwt.refresh(body.refreshToken()), "Đã làm mới phiên đăng nhập", request);
    }

    @PostMapping("/forgot-password")
    ResponseEntity<ApiResponse<Void>> forgot(@Valid @RequestBody AuthPayloads.EmailRequest body, HttpServletRequest request) {
        auth.forgot(body.email());
        return ApiResponse.ok(null, "Nếu email đủ điều kiện, mã xác minh sẽ được gửi", request);
    }

    @PostMapping("/forgot-password/verify-otp")
    ResponseEntity<ApiResponse<AuthResponses.ResetGrant>> verifyReset(
            @Valid @RequestBody AuthPayloads.OtpRequest body, HttpServletRequest request) {
        return ApiResponse.ok(auth.verifyReset(body), "Xác minh mã đặt lại mật khẩu thành công", request);
    }

    @PostMapping("/reset-password")
    ResponseEntity<ApiResponse<Void>> reset(@Valid @RequestBody AuthPayloads.Reset body, HttpServletRequest request) {
        auth.reset(body);
        return ApiResponse.ok(null, "Đã đặt lại mật khẩu. Vui lòng đăng nhập lại", request);
    }

    @PostMapping("/change-password")
    ResponseEntity<ApiResponse<Void>> change(@CurrentJwt Jwt principal,
            @Valid @RequestBody AuthPayloads.ChangePassword body, HttpServletRequest request) {
        auth.changePassword(principal.getSubject(), body);
        return ApiResponse.ok(null, "Đã đổi mật khẩu. Vui lòng đăng nhập lại", request);
    }
}
