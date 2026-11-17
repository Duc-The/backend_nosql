package vn.ducthe.organic.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import vn.ducthe.organic.common.response.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(ApiException exception, HttpServletRequest request) {
        ApiError error = exception.error();
        return ResponseEntity.status(error.status()).body(ApiResponse.failure(
                error.code(), error.message(), error.name(), List.of(), request));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateKeyException exception, HttpServletRequest request) {
        return handleBusiness(new ApiException(ApiError.CONFLICT), request);
    }

    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleSecurity(RuntimeException exception, HttpServletRequest request) {
        HttpStatus status = exception instanceof AuthenticationException
                ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(httpError(status, request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected API error at {}", request.getRequestURI(), exception);
        return ResponseEntity.internalServerError().body(httpError(HttpStatus.INTERNAL_SERVER_ERROR, request));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        if (exception instanceof MethodArgumentNotValidException invalid) {
            List<ApiResponse.Violation> details = invalid.getBindingResult().getAllErrors().stream()
                    .map(error -> new ApiResponse.Violation(
                            error instanceof FieldError field ? field.getField() : null, error.getDefaultMessage()))
                    .toList();
            return validationResponse(exception, details, headers, request, servletRequest);
        }
        if (exception instanceof HandlerMethodValidationException invalid && !invalid.isForReturnValue()) {
            List<ApiResponse.Violation> details = new ArrayList<>();
            for (ParameterValidationResult result : invalid.getParameterValidationResults()) {
                String parameter = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(error -> details.add(new ApiResponse.Violation(
                        error instanceof FieldError field ? field.getField() : parameter, error.getDefaultMessage())));
            }
            invalid.getCrossParameterValidationResults().forEach(error ->
                    details.add(new ApiResponse.Violation(null, error.getDefaultMessage())));
            return validationResponse(exception, details, headers, request, servletRequest);
        }
        if (status.is5xxServerError()) {
            log.error("API error at {}", servletRequest.getRequestURI(), exception);
        }
        return super.handleExceptionInternal(exception, httpError(status, servletRequest), headers, status, request);
    }

    private ResponseEntity<Object> validationResponse(
            Exception exception, List<ApiResponse.Violation> details, HttpHeaders headers,
            WebRequest request, HttpServletRequest servletRequest) {
        ApiError error = ApiError.VALIDATION_ERROR;
        return super.handleExceptionInternal(exception, ApiResponse.failure(
                error.code(), error.message(), error.name(), details, servletRequest),
                headers, error.status(), request);
    }

    private ApiResponse<Void> httpError(HttpStatusCode status, HttpServletRequest request) {
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        String code = knownStatus == null ? "HTTP_" + status.value() : knownStatus.name();
        String message = switch (status.value()) {
            case 400 -> "Yêu cầu không hợp lệ";
            case 401 -> "Bạn cần đăng nhập";
            case 403 -> "Bạn không có quyền thực hiện thao tác này";
            case 404 -> "Không tìm thấy tài nguyên";
            case 405 -> "Phương thức HTTP không được hỗ trợ";
            case 409 -> "Dữ liệu bị xung đột";
            case 413 -> "Nội dung gửi lên quá lớn";
            case 415 -> "Định dạng nội dung không được hỗ trợ";
            case 500 -> "Đã xảy ra lỗi hệ thống";
            default -> status.is5xxServerError() ? "Dịch vụ gặp lỗi" : "Không thể xử lý yêu cầu";
        };
        return ApiResponse.failure(status.value() * 100, message, code, List.of(), request);
    }
}
