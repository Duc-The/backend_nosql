package vn.ducthe.organic.exception;

public class ApiException extends RuntimeException {
    private final ApiError error;

    public ApiException(ApiError error) {
        super(error.message());
        this.error = error;
    }

    public ApiError error() {
        return error;
    }
}
