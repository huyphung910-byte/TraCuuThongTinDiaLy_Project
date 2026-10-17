# GeoViet — Giao diện HTML theo ảnh mẫu

Bản này được bổ sung trực tiếp từ ZIP TraCuuThongTinDiaLy_Project-main (1).zip.
Tông xanh ngọc, nền xanh mint, thẻ bo góc, menu bên trái; bố cục tự thu gọn trên điện thoại.

## Chạy trên Windows

1. Giải nén ZIP vào một thư mục mới. Cài JDK 17 trở lên, bảo đảm `java -version` và `javac -version` chạy được trong PowerShell.
2. Bật MySQL trong XAMPP, chuẩn bị database theo README.md hiện có; giữ cấu hình kết nối database/API phù hợp trong server/config.properties.
3. Nếu chưa có cặp khóa server hoặc máy chủ báo thiếu khóa, chạy init_keys.bat. Công khai khóa mới tới client theo hướng dẫn cũ. Không tạo lại khóa khi server đang chạy.
4. Chạy build.bat, sau đó run_server.bat và giữ cửa sổ máy chủ mở.
5. Chạy run_web.bat. File này biên dịch rồi khởi động cầu nối web.
6. Mở http://127.0.0.1:8088 trong Chrome hoặc Edge. Đăng ký/đăng nhập bằng tài khoản trong database.

Mở trực tiếp web/index.html chỉ dùng để xem bố cục. Đăng nhập và tra cứu thật phải qua run_web.bat.
Nếu cổng 8088 đang được sử dụng, dừng tiến trình đang chiếm cổng trước khi chạy.
Nhấn Ctrl+C trong cửa sổ run_web để dừng cầu nối. Phiên không hoạt động được dọn sau 30 phút; đóng trình duyệt không bảo đảm ngắt phiên ngay.

## Chức năng giao diện

- Đăng nhập, đăng ký, đăng xuất; xử lý tài khoản theo backend hiện có.
- Tìm thành phố, chọn kết quả, xem tọa độ, dân số, múi giờ, bản đồ, thời tiết và địa điểm lân cận.
- Quốc gia: thủ đô, ngôn ngữ, tiền tệ, dân số, diện tích, khí hậu, kinh tế và văn hóa.
- Lưu/xóa địa điểm với ghi chú; xem lịch sử tra cứu; cập nhật hồ sơ và đổi mật khẩu.
- So sánh hai thành phố hoặc hai quốc gia, đúng giao thức máy chủ.
- Xem thời tiết hiện tại; đăng ký/hủy cảnh báo; lấy thông báo đẩy từ socket mỗi 12 giây khi trình duyệt đang mở.
- Lập lịch trình 1–7 ngày từ nguồn địa điểm và thời tiết hiện có.

Không thêm dự báo nhiều ngày vì backend hiện tại chỉ cung cấp thời tiết hiện tại.
Các dữ liệu trả về thiếu được hiển thị là chưa có dữ liệu; không thay bằng số liệu giả.
Bản đồ dùng OpenStreetMap và cần mạng. Các trường quốc gia có thể giữ ngôn ngữ nguồn API.
Các liên kết/hình du lịch ngoài nguồn backend và bảng dự báo trong ảnh mẫu không được giả lập thành dữ liệu thực.

## Các file bổ sung

- web/index.html: toàn bộ HTML/CSS/JavaScript, không cần npm.
- client/src/WebClientMain.java: HTTP localhost, một phiên web tương ứng một ClientSocketManager bảo mật.
- run_web.bat: biên dịch và chạy giao diện web.
- HUONG_DAN_GIAO_DIEN_WEB.md: hướng dẫn này.

Các file nguồn cũ được giữ nguyên; client Swing vẫn chạy bằng run_client.bat.
Luồng: Trình duyệt → HTTP trên 127.0.0.1 → WebClientMain → ClientSocketManager → Socket bảo mật → Server → database/API.
Không đổi server thành REST, không đưa API key hay khóa riêng vào JavaScript.
Cầu nối chỉ nghe tại 127.0.0.1:8088; không thiết kế để public Internet.

## Kiểm tra đã thực hiện

- Biên dịch toàn bộ client (bao gồm WebClientMain) và server bằng Java 17: thành công.
- Kiểm tra cú pháp JavaScript: thành công.
- Khởi động cầu nối, kiểm tra trả trang HTML: thành công.
- Kiểm tra chặn Origin khác và trả lỗi rõ khi máy chủ Socket chưa chạy: thành công.
- Chưa xác minh trình duyệt bằng hình ảnh và chưa thử toàn bộ luồng với MySQL/API thực trong môi trường của bạn.
