package vn.ducthe.organic.exception;

import org.springframework.http.HttpStatus;

public enum ApiError {
    INVALID_CREDENTIALS(40101, HttpStatus.UNAUTHORIZED, "Thông tin đăng nhập không hợp lệ"),
    ACCOUNT_LOCKED(40301, HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa"),
    ACCOUNT_UNVERIFIED(40302, HttpStatus.FORBIDDEN, "Tài khoản chưa xác minh email"),
    INVALID_OTP(40013, HttpStatus.BAD_REQUEST, "Mã OTP sai hoặc đã hết hạn"),
    INVALID_RESET_TOKEN(40014, HttpStatus.BAD_REQUEST, "Quyền đặt lại mật khẩu đã hết hạn hoặc không hợp lệ"),
    INVALID_PRICE_RANGE(40015, HttpStatus.valueOf(422), "Giá tối thiểu phải nhỏ hơn hoặc bằng giá tối đa"),
    INVALID_TOKEN(40102, HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn hoặc không hợp lệ"),
    ACCESS_DENIED(40300, HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    OTP_RATE_LIMIT(42901, HttpStatus.TOO_MANY_REQUESTS, "Vui lòng chờ trước khi yêu cầu mã mới"),
    EMAIL_UNAVAILABLE(50301, HttpStatus.SERVICE_UNAVAILABLE, "Tạm thời chưa thể gửi email"),
    GOOGLE_UNAVAILABLE(50302, HttpStatus.SERVICE_UNAVAILABLE, "Đăng nhập Google tạm thời không khả dụng"),
    VALIDATION_ERROR(40012, HttpStatus.valueOf(422), "Dữ liệu đầu vào không hợp lệ"),
    RESOURCE_NOT_FOUND(40400, HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    CONFLICT(40900, HttpStatus.CONFLICT, "Dữ liệu bị xung đột"),
    CART_ITEM_UNAVAILABLE(40901, HttpStatus.CONFLICT, "Quy cách sản phẩm hiện không mở bán"),
    INSUFFICIENT_STOCK(40902, HttpStatus.CONFLICT, "Số lượng hàng còn lại không đủ"),
    CART_LIMIT(40903, HttpStatus.CONFLICT, "Giỏ hàng vượt giới hạn số dòng hoặc số lượng"),
    EMPTY_CART(40904, HttpStatus.CONFLICT, "Giỏ hàng chưa có sản phẩm"),
    DELIVERY_ADDRESS_REQUIRED(42201, HttpStatus.valueOf(422), "Vui lòng chọn địa chỉ giao hàng"),
    ORDER_NOT_CANCELLABLE(40905, HttpStatus.CONFLICT, "Đơn hàng đã qua thời hạn hủy hoặc không còn chờ xử lý"),
    CHECKOUT_KEY_REUSED(40906, HttpStatus.CONFLICT, "Khóa đặt hàng đã dùng cho yêu cầu khác"),
    ORDER_RETRY(50303, HttpStatus.SERVICE_UNAVAILABLE, "Chưa thể xác nhận thao tác, vui lòng thử lại với cùng yêu cầu"),
    ORDER_STORAGE_UNAVAILABLE(50304, HttpStatus.SERVICE_UNAVAILABLE, "Kho dữ liệu chưa hỗ trợ giao dịch đặt hàng"),
    PAYMENT_GATEWAY_UNAVAILABLE(50305, HttpStatus.SERVICE_UNAVAILABLE, "Cổng thanh toán tạm thời không khả dụng"),
    PAYMENT_NOT_PAYABLE(40907, HttpStatus.CONFLICT, "Đơn hàng hiện không thể thanh toán"),
    INVALID_PAYMENT_SIGNATURE(40016, HttpStatus.BAD_REQUEST, "Chữ ký thông báo thanh toán không hợp lệ"),
    INVALID_PAYMENT_NOTIFICATION(40017, HttpStatus.BAD_REQUEST, "Thông báo thanh toán không hợp lệ"),
    INVALID_PAYMENT_AMOUNT(42202, HttpStatus.valueOf(422), "Tổng tiền không phù hợp với phương thức thanh toán"),
    PROMOTION_UNAVAILABLE(40908, HttpStatus.CONFLICT, "Mã giảm giá không hợp lệ hoặc đã hết lượt sử dụng"),
    PROMOTION_MINIMUM_NOT_MET(42203, HttpStatus.valueOf(422), "Tiền hàng chưa đủ điều kiện áp mã giảm giá"),
    INSUFFICIENT_POINTS(40909, HttpStatus.CONFLICT, "Điểm thưởng không đủ để đổi voucher"),
    STAFF_SELF_CHANGE(40910, HttpStatus.CONFLICT, "Không thể tự khóa, đặt lại mật khẩu hoặc đổi vai trò của chính mình từ trang quản trị"),
    STAFF_ROLE_INACTIVE(40911, HttpStatus.CONFLICT, "Vai trò này hiện không hoạt động"),
    CATEGORY_CYCLE(42204, HttpStatus.valueOf(422), "Danh mục cha không được là chính nó hoặc danh mục con"),
    INVALID_COMPARE_AT_PRICE(42205, HttpStatus.valueOf(422), "Giá so sánh phải lớn hơn hoặc bằng giá bán"),
    CATALOG_IMAGE_INVALID(42206, HttpStatus.valueOf(422), "Vui lòng tải lên ảnh JPEG, PNG hoặc WebP hợp lệ"),
    CATALOG_IMAGE_TOO_LARGE(41301, HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh vượt quá 5 MB"),
    CATALOG_IMAGE_LIMIT(40912, HttpStatus.CONFLICT, "Sản phẩm đã đạt giới hạn ảnh"),
    CATALOG_IMAGE_PROVIDER(50306, HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ lưu ảnh tạm thời không khả dụng"),
    SUPPLIER_INACTIVE(40913, HttpStatus.CONFLICT, "Nhà cung cấp hiện không hoạt động"),
    RECEIPT_NOT_DRAFT(40914, HttpStatus.CONFLICT, "Chỉ phiếu nhập nháp mới được thay đổi"),
    RECEIPT_QUANTITY_INVALID(42207, HttpStatus.valueOf(422), "Số lượng từ chối không được vượt số lượng nhận"),
    RECEIPT_REJECTION_REASON_REQUIRED(42208, HttpStatus.valueOf(422), "Vui lòng ghi lý do từ chối hàng"),
    RECEIPT_BATCH_INVALID(42209, HttpStatus.valueOf(422), "Hàng đạt cần mã lô, xuất xứ và ngày sản xuất, hạn dùng hợp lệ"),
    EMPLOYEE_REQUIRED(40303, HttpStatus.FORBIDDEN, "Tài khoản cần hồ sơ nhân viên đang hoạt động để xử lý phiếu nhập"),
    BATCH_STATUS_REASON_REQUIRED(42210, HttpStatus.valueOf(422), "Vui lòng ghi lý do ngừng bán lô hàng"),
    BATCH_NOT_SELLABLE(40915, HttpStatus.CONFLICT, "Lô hết hạn hoặc thiếu hạn dùng không thể mở bán"),
    STOCK_HOLD_EXPIRED(40916, HttpStatus.CONFLICT, "Đơn hàng đã quá hạn giữ tồn"),
    STOCK_ALLOCATION_INVALID(40917, HttpStatus.CONFLICT, "Dữ liệu giữ tồn không khớp với số lượng trong đơn"),
    ORDER_NOT_ISSUABLE(40918, HttpStatus.CONFLICT, "Trạng thái đơn hoặc thanh toán chưa cho phép xuất kho"),
    ORDER_STATUS_TRANSITION_INVALID(40919, HttpStatus.CONFLICT, "Không thể chuyển đơn sang trạng thái này"),
    ORDER_ASSIGNEE_REQUIRED(40920, HttpStatus.CONFLICT, "Đơn hàng cần có nhân viên phụ trách"),
    ORDER_ASSIGNEE_INVALID(42211, HttpStatus.valueOf(422), "Người phụ trách phải là chủ cửa hàng hoặc nhân viên bán hàng đang hoạt động"),
    ORDER_PAYMENT_REQUIRED(40921, HttpStatus.CONFLICT, "Đơn hàng chưa được xác nhận thanh toán"),
    PROMOTION_SCOPE_NOT_MET(42212, HttpStatus.valueOf(422), "Giỏ hàng chưa đủ sản phẩm hoặc lô thuộc khuyến mãi"),
    PROMOTION_BATCH_NO_LONGER_AVAILABLE(40922, HttpStatus.CONFLICT, "Lô áp dụng khuyến mãi không còn đủ điều kiện xuất kho"),
    PROMOTION_RULE_INVALID(42213, HttpStatus.valueOf(422), "Điều kiện, thời gian hoặc đối tượng khuyến mãi không hợp lệ"),
    PROMOTION_LIMIT_BELOW_USAGE(40923, HttpStatus.CONFLICT, "Giới hạn lượt dùng thấp hơn số lượt đã sử dụng"),
    PROMOTION_MANAGED_BY_LOYALTY(40924, HttpStatus.CONFLICT, "Khuyến mãi đổi điểm được quản lý bởi cấu hình điểm thưởng"),
    REVIEW_PURCHASE_REQUIRED(40304, HttpStatus.FORBIDDEN, "Chỉ khách đã hoàn tất và thanh toán đơn chứa sản phẩm mới được đánh giá"),
    REVIEW_ALREADY_EXISTS(40925, HttpStatus.CONFLICT, "Bạn đã đánh giá sản phẩm này"),
    REVIEW_MEDIA_INVALID(42215, HttpStatus.valueOf(422), "Chỉ hỗ trợ tối đa 4 ảnh JPEG, PNG, WebP và 1 video MP4 hoặc WebM hợp lệ"),
    REVIEW_MEDIA_TOO_LARGE(41302, HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh tối đa 5 MB và video tối đa 25 MB"),
    REVIEW_MEDIA_UNAVAILABLE(50307, HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ lưu ảnh và video tạm thời không khả dụng"),
    REPORT_DATE_RANGE_INVALID(42214, HttpStatus.valueOf(422), "Khoảng ngày báo cáo phải hợp lệ và không quá 366 ngày");

    private final int code;
    private final HttpStatus status;
    private final String message;

    ApiError(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
