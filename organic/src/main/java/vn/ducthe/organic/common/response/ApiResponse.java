package vn.ducthe.organic.common.response;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;

public record ApiResponse<T>(
        boolean success, int code, String message, T data, ErrorBody error, long timestamp, String path) {

    public static <T> ResponseEntity<ApiResponse<T>> ok(
            T data, String message, HttpServletRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(
                true, 20000, message, data, null, System.currentTimeMillis(), request.getRequestURI()));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(
            T data, String message, HttpServletRequest request) {
        return ResponseEntity.status(201).body(new ApiResponse<>(
                true, 20100, message, data, null, System.currentTimeMillis(), request.getRequestURI()));
    }

    public static ApiResponse<Void> failure(
            int code, String message, String errorCode, List<Violation> details, HttpServletRequest request) {
        return new ApiResponse<>(false, code, message, null, new ErrorBody(errorCode, details),
                System.currentTimeMillis(), request.getRequestURI());
    }

    public record ErrorBody(String code, List<Violation> details) {}

    public record Violation(String field, String message) {}
}
