# Đề tài 12: Tra cứu thông tin địa lý

Ứng dụng tra cứu thông tin địa lý được xây dựng theo mô hình **Java Socket Client-Server**, sử dụng **MySQL trên XAMPP** để lưu trữ dữ liệu. Người dùng thao tác trên Client; Server tiếp nhận yêu cầu, xử lý nghiệp vụ, truy vấn cơ sở dữ liệu và trả kết quả về cho Client.

## Chức năng chính

- Đăng ký, đăng nhập và quản lý tài khoản người dùng.
- Tra cứu thông tin thành phố, quốc gia và địa điểm theo từ khóa.
- Hiển thị thông tin địa lý như tên địa điểm, vĩ độ và kinh độ.
- Lưu các địa điểm yêu thích kèm ghi chú cá nhân.
- Xem lịch sử các lần tra cứu.
- Tra cứu và lưu thông tin thời tiết theo địa điểm.
- Theo dõi thời tiết tại các địa điểm đã đăng ký.
- Thiết lập điều kiện cảnh báo thời tiết, ví dụ mưa lớn, bão hoặc nhiệt độ cao.
- Lưu dữ liệu thời tiết vào bộ nhớ đệm để hỗ trợ truy xuất nhanh.

## Công nghệ sử dụng

- **Java**: xây dựng Client, Server và xử lý giao tiếp mạng.
- **Java Socket**: kết nối TCP giữa Client và Server.
- **JDBC**: kết nối và thao tác với MySQL.
- **MySQL**: quản lý dữ liệu tài khoản, địa điểm, lịch sử, thời tiết và cảnh báo.
- **XAMPP**: cung cấp MySQL và phpMyAdmin để khởi tạo, quản lý cơ sở dữ liệu.
- **Git/GitHub**: quản lý mã nguồn (nếu sử dụng).

## Cấu trúc thư mục

```text
tra_cuu_thong_tin_dia_ly/
├── client/                         # Mã nguồn Java Client
├── server/                         # Mã nguồn Java Server
├── database/
│   └── tra_cuu_dia_ly.sql          # Script khởi tạo cơ sở dữ liệu
└── README.md
```

## Yêu cầu môi trường

- JDK 8 trở lên.
- XAMPP có bật **MySQL** (và Apache nếu sử dụng phpMyAdmin).
- IDE hỗ trợ Java như IntelliJ IDEA, Eclipse hoặc NetBeans.
- Thư viện **MySQL Connector/J** tương thích với phiên bản JDK đang sử dụng.

## Hướng dẫn cài đặt và chạy dự án

### 1. Tải mã nguồn

```bash
git clone <URL_REPOSITORY>
cd tra_cuu_thong_tin_dia_ly
```

Nếu đã có mã nguồn trên máy, chỉ cần mở thư mục dự án bằng IDE.

### 2. Khởi động MySQL bằng XAMPP

1. Mở **XAMPP Control Panel**.
2. Nhấn **Start** tại dịch vụ **MySQL**.
3. Có thể nhấn **Admin** để mở phpMyAdmin.

### 3. Tạo cơ sở dữ liệu

1. Mở phpMyAdmin hoặc MySQL client.
2. Chọn chức năng **Import**.
3. Chọn file [`database/tra_cuu_dia_ly.sql`](database/tra_cuu_dia_ly.sql).
4. Thực thi script và kiểm tra database `tra_cuu_dia_ly` đã được tạo.

Script đã tạo sẵn các bảng `tai_khoan`, `dia_diem_da_luu`, `lich_su_tra_cuu`, `bo_nho_dem_thoi_tiet` và `theo_doi_thoi_tiet`, kèm dữ liệu mẫu.

### 4. Cấu hình kết nối database

Trong phần cấu hình của Server, cập nhật các thông tin JDBC tương ứng với máy local:

```java
String url = "jdbc:mysql://localhost:3306/tra_cuu_dia_ly?useSSL=false&serverTimezone=UTC";
String username = "root";
String password = ""; // Mật khẩu MySQL trong XAMPP
```

Nếu MySQL sử dụng cổng hoặc mật khẩu khác, thay đổi các giá trị trên cho phù hợp. Đảm bảo đã thêm file `mysql-connector-j-*.jar` vào thư viện của project.

### 5. Cấu hình địa chỉ Socket

Client và Server cần dùng cùng các thông số kết nối:

```text
Host: localhost
Port: port được khai báo trong Server
```

Khi chạy trên hai máy khác nhau, thay `localhost` bằng địa chỉ IP của máy chạy Server và mở port tương ứng trong tường lửa.

### 6. Biên dịch và chạy Server

Mở project bằng IDE, nạp thư viện MySQL Connector/J, sau đó chạy class có hàm `main` của Server. Server cần được khởi động trước để lắng nghe kết nối từ Client.

Nếu chạy bằng dòng lệnh, thay `<ServerMainClass>` bằng tên class Server thực tế:

```bash
javac -cp "mysql-connector-j-*.jar" -d out server/*.java
java -cp "out;mysql-connector-j-*.jar" <ServerMainClass>
```

> Trên Linux/macOS, dấu phân cách classpath là `:` thay cho `;`.

### 7. Biên dịch và chạy Client

Sau khi Server đã chạy, mở một cửa sổ chạy khác và chạy class có hàm `main` của Client. Thay `<ClientMainClass>` bằng tên class Client thực tế:

```bash
javac -cp out -d out client/*.java
java -cp "out;mysql-connector-j-*.jar" <ClientMainClass>
```

Client sẽ kết nối đến Server, sau đó người dùng có thể đăng nhập và thực hiện các chức năng tra cứu.

## Tài khoản mẫu

Database có sẵn một số tài khoản để kiểm tra:

| Tên đăng nhập | Mật khẩu |
| ------------- | -------- |
| `nguyenvana`  | `123456` |
| `phunggiahuy` | `123456` |
| `tranvanb`    | `123456` |

> Các tài khoản và mật khẩu trên chỉ phục vụ mục đích demo. Khi triển khai thực tế, không nên lưu mật khẩu dạng văn bản thuần.

## Xử lý lỗi thường gặp

- **Không kết nối được MySQL**: kiểm tra MySQL trong XAMPP đã chạy, đúng port, tên database và thông tin đăng nhập.
- **Không tìm thấy driver JDBC**: kiểm tra `mysql-connector-j` đã được thêm vào classpath của Server.
- **Client không kết nối được Server**: khởi động Server trước, kiểm tra host/port ở cả hai phía.
- **Port đã được sử dụng**: đổi port trong Server và cập nhật lại port tương ứng trong Client.
