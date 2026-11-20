# Dữ liệu mặc định Mùa Xanh

Dữ liệu được tạo khi ứng dụng khởi động nếu `app.seed.enabled=true`.
Mặc định đã bật trong `application.yml`; biến `APP_SEED_ENABLED=false` sẽ tắt.

## Tài khoản

Mật khẩu ban đầu của các tài khoản dưới đây: `Organic@123456`.
Mật khẩu được lưu dưới dạng BCrypt trong MongoDB.

| Tên đăng nhập | Vai trò |
| --- | --- |
| admin | ADMIN |
| warehouse | WAREHOUSE_STAFF |
| sales | SALES_STAFF |
| nguyenminhanh1 | CUSTOMER |
| tranminhanh2 | CUSTOMER |
| leminhanh3 | CUSTOMER |

## Dữ liệu mẫu

- 3 danh mục con và 1 danh mục gốc, 3 nhà cung cấp.
- 6 sản phẩm, mỗi sản phẩm có 2 quy cách, tổng 12 SKU.
- 24 lô hàng cùng phiếu nhập và chi tiết nhập tương ứng.
- 24 đơn COD gồm hoàn tất, chờ xử lý và đã hủy; có chi tiết đơn, phân bổ lô và thanh toán.
- Điểm thưởng được cộng theo cấu hình tích điểm hiện hành từ 12 đơn hoàn tất đã thanh toán; có lịch sử điểm và cờ đã xử lý trên đơn.
- Khuyến mãi công khai `ORGANIC10`: giảm 10%, tối đa 30.000đ, đơn tối thiểu 100.000đ.
- Khuyến mãi `MUAXANH20K`: giảm 20.000đ, đơn tối thiểu 100.000đ, chỉ khách đã được cấp voucher sử dụng.
- Mỗi khách mẫu có 1 voucher: `MX-WELCOME-1`, `MX-WELCOME-2`, `MX-WELCOME-3`, sử dụng tối đa 1 lần.

Seeder chỉ thêm các bản ghi mẫu còn thiếu bằng ID cố định. Khởi động lại không đặt lại mật khẩu, số điểm, lượt dùng voucher hoặc tồn kho đã thay đổi. Dữ liệu khác đang có được giữ nguyên.

## Kiểm tra tự động

`mvnw.cmd -Dtest=SampleDataSeederTest test` kiểm tra tạo tài khoản/quyền, điểm thưởng và chạy lại seeder không làm trùng hoặc ghi đè dữ liệu. Bài kiểm tra dùng database MongoDB riêng có tên bắt đầu `organic_seed_test_` và xóa database kiểm tra sau khi kết thúc. MongoDB local cần hỗ trợ transaction (replica set như cấu hình dự án).
