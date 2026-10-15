# Đề tài 12: Tra cứu thông tin địa lý

Ứng dụng tra cứu thông tin địa lý được xây dựng theo mô hình **máy khách – máy chủ qua Java Socket**, sử dụng **MySQL trên XAMPP** để lưu trữ dữ liệu. Người dùng thao tác trên máy khách; máy chủ tiếp nhận yêu cầu, xử lý nghiệp vụ, truy vấn cơ sở dữ liệu và trả kết quả về cho máy khách.

## Chức năng chính

- Đăng ký, đăng nhập và quản lý tài khoản người dùng.
- Tra cứu thông tin thành phố, quốc gia và địa điểm theo từ khóa.
- Hiển thị thông tin địa lý như tên địa điểm, vĩ độ và kinh độ.
- Lưu các địa điểm kèm ghi chú cá nhân.
- Xem lịch sử các lần tra cứu.
- Tra cứu và lưu thông tin thời tiết theo địa điểm.
- Theo dõi thời tiết tại các địa điểm đã đăng ký.
- Thiết lập điều kiện cảnh báo thời tiết, ví dụ mưa lớn, bão hoặc nhiệt độ cao.
- Lưu dữ liệu thời tiết vào bộ nhớ đệm để hỗ trợ truy xuất nhanh.

## Ngôn ngữ hiển thị

Giao diện, hộp thoại, thông báo kết nối, cảnh báo thời tiết và thông báo của các
tệp `.bat` dùng tiếng Việt có dấu. Máy khách dùng định dạng số và ngày giờ Việt
Nam; tên quốc gia, khu vực, tiền tệ, ngôn ngữ và các tên thành phố có bản dịch
phổ biến được Việt hóa khi hiển thị. OpenWeather được yêu cầu trả mô tả tiếng
Việt; bộ nhớ đệm tiếng Việt dùng khóa riêng. Wikipedia ưu tiên bài viết tiếng
Việt; khi không có địa điểm trong nguồn này, giao diện ghi rõ nguồn bài viết
tiếng Anh. Tên riêng chưa có bản dịch và nội dung do người dùng nhập được giữ
nguyên. Chạy `test_comparison.bat` để kiểm tra phần Việt hóa và bảng so sánh.

### Tiếng Việt trong terminal Windows

Client và Server cấu hình cả `System.out` và `System.err` thành UTF-8 ngay khi
khởi động. Script chạy đặt mã trang terminal 65001 và các tùy chọn đầu ra Java;
`file.encoding` riêng lẻ có thể chưa đủ trên Windows.

Trong VS Code, cấu hình workspace dùng terminal **PowerShell UTF-8** cho **Run
Java** và **Debug Java**. Đóng terminal cũ bằng biểu tượng thùng rác, mở terminal
mới, rồi chạy `run_client.bat` hoặc chạy lại `ClientMain`. Cấu hình chỉ áp dụng
cho workspace dự án. Hai file Java và phần `.vscode` đều lưu dạng UTF-8.

Nếu dùng một cửa sổ PowerShell đã mở từ trước, chạy:

```powershell
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
chcp.com 65001
.\run_client.bat
```

Các dòng đã in lỗi dấu sẽ giữ nguyên; kiểm tra những dòng mới sau khi khởi động
lại. Font terminal cần hỗ trợ tiếng Việt, chẳng hạn Consolas hoặc Cascadia Mono.

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

- JDK 17 trở lên.
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

Sửa `server/config.properties` theo MySQL trên máy A:

```properties
db.host=127.0.0.1
db.port=3306
db.name=tra_cuu_dia_ly
db.user=root
db.password=
```

Nếu MySQL sử dụng cổng hoặc mật khẩu khác, thay đổi các giá trị trên cho phù hợp. Đảm bảo đã thêm file `mysql-connector-j-*.jar` vào thư viện của project.

### 5. Cấu hình địa chỉ Socket

Sửa `client/config.properties`: `server.host` là IPv4 LAN của máy A,
`server.port` trùng với cổng trong `server/config.properties`:

```text
server.host=192.168.1.10
server.port=8888
```

Server dùng `server.bindAddress=0.0.0.0` để nhận kết nối qua LAN.
Các file cấu hình được tìm từ thư mục gốc project hoặc thư mục `client`/`server`.
Có thể chỉ định file khác bằng `-Dclient.config=...` hoặc `-Dserver.config=...`;
thuộc tính `-Dserver.host`, `-Dserver.port` vẫn được ưu tiên để chạy từ IDE.

### Demo trên hai máy qua LAN

1. **Máy A:** chạy MySQL, import `database/tra_cuu_dia_ly.sql`, chỉnh cấu hình DB,
   chạy `init_keys.bat` một lần rồi chạy `run_server.bat`.
2. Dùng `ipconfig` trên máy A để lấy IPv4 của card mạng LAN/Wi-Fi đang dùng
   (ví dụ `192.168.1.10`). Cho phép Java hoặc TCP 8888 inbound trong Windows
   Firewall của máy A cho mạng Private. Hai máy phải cùng mạng và được phép
   giao tiếp với nhau.
3. **Máy B:** dùng bản project có JDK và các thư viện Client, chép khóa công khai
   của máy A vào `client/security/server-public.key`; khóa riêng giữ trên máy A.
   Sửa `client/config.properties` với IP máy A và cổng Server, chạy `run_client.bat`.
   Máy B không cần MySQL. Các script hiện biên dịch toàn bộ project nên bản demo
   dùng đủ thư mục nguồn và thư viện của project trên cả hai máy.
4. Trên máy B, kiểm tra `Test-NetConnection 192.168.1.10 -Port 8888`, đăng nhập,
   tra cứu và đăng ký theo dõi để nhận cảnh báo Server push khi điều kiện khớp.
   Nếu đổi IP/cổng, cập nhật file cấu hình rồi khởi động lại ứng dụng.

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

## Mã hóa giao tiếp Client – Server

Toàn bộ JSON (yêu cầu, phản hồi và cảnh báo thời tiết) đi qua `SecureChannel`,
không gửi văn bản JSON trực tiếp qua TCP. Client tạo ngẫu nhiên hai khóa AES-256
riêng cho mỗi kết nối, một khóa cho mỗi chiều truyền. Các khóa chỉ nằm trong bộ
nhớ của phiên và được trao đổi bằng `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`,
với SHA-256 cho cả OAEP và MGF1. Server dùng cặp khóa RSA 3072 bit.

Dữ liệu dùng `AES/GCM/NoPadding`, nonce 12 byte và thẻ xác thực 128 bit.
Nonce được tạo từ số thứ tự tăng dần; hai chiều dùng khóa khác nhau nên không
trùng nonce trên cùng khóa. Số thứ tự và chiều truyền được xác thực bằng AAD;
frame bị sửa, sai thứ tự hoặc phát lại sẽ làm đóng kết nối. Mỗi frame có tiền tố
độ dài 4 byte, giới hạn 1 MiB. Hai bên xác nhận khóa trước khi xử lý JSON.

Chạy trên Windows từ thư mục gốc dự án:

```bat
build.bat
init_keys.bat
run_server.bat
run_client.bat
```

`init_keys.bat` chỉ chạy lần đầu, tạo khóa tại `server/security/` và sao chép
public key sang `client/security/server-public.key`. Nếu khóa đã tồn tại, script
từ chối ghi đè. Giữ private key trên Server; khi chạy Client trên máy khác, chép
public key đáng tin cậy sang máy đó và chạy `run_client.bat <IP_SERVER>`.
Client đối chiếu public key Server với bản được cấp trước khi trao đổi khóa.

Khi chạy bằng IDE, dùng `ClientMain` và `ServerMain`. Có thể cấu hình qua VM
options `-Dserver.host=...`, `-Dserver.port=8888`,
`-Dsecure.serverPublicKey=...` (Client), `-Dsecure.keyDir=...` (Server).
Client và Server phải cùng dùng phiên bản giao thức này.

`run_server.bat` và `run_client.bat` tự chạy `build.bat` trước khi khởi động,
và dừng nếu biên dịch lỗi. Sau khi cập nhật mã nguồn, đóng Client và nhấn
**Ctrl+C** trong cửa sổ Server cũ trước khi chạy lại cả hai. Một Server đang
chạy vẫn giữ mã Java đã nạp trong bộ nhớ dù các tệp `.class` đã được cập nhật.
Nếu Client mới gửi yêu cầu tải địa điểm đã lưu không kèm `userId` nhưng
Server cũ còn yêu cầu trường này, có thể xuất hiện thông báo
**Dữ liệu JSON của yêu cầu không hợp lệ**. Hãy khởi động lại Server từ đúng
thư mục dự án; Server mới lấy ID tài khoản từ phiên đăng nhập.

Kiểm tra mã hóa độc lập với MySQL bằng `test_security.bat`.

## Tra cứu thành phố từ API

`SEARCH_LOCATION` gọi Open-Meteo Geocoding, không đọc bảng `dia_diem_da_luu`.
Bảng kết quả hiển thị tên thành phố, quốc gia, tỉnh/vùng, tọa độ và dân số
(nếu nguồn GeoNames cung cấp). Chọn một dòng để tải thời tiết hiện tại từ
OpenWeather và các địa điểm lân cận trong bán kính 10 km từ Wikipedia Geosearch.
Thời tiết và địa điểm lân cận được truy vấn theo tọa độ của thành phố đã chọn.
Nguồn này cung cấp địa điểm liên quan; không cung cấp giá/phòng khách sạn.

Tab **Địa điểm lân cận** hiển thị thẻ gồm ảnh đại diện (nếu có), tên,
khoảng cách đường thẳng, mô tả ngắn từ Wikipedia và nút **Xem trên Wikipedia**
để mở trình duyệt. Ảnh tải nền trên Client; thiếu ảnh/mô tả vẫn giữ thẻ địa điểm.
Thông tin này chỉ dùng để hiển thị, không tự lưu database.
Ảnh lấy qua [PageImages](https://www.mediawiki.org/wiki/API:Pageimages).

Server cần kết nối Internet và API key OpenWeather để lấy thời tiết. Nếu API lỗi hoặc không cung cấp dân số/địa điểm,
giao diện thông báo rõ, không thay bằng dữ liệu giả. Dân số là số liệu từ nguồn,
không bảo đảm là thống kê của năm hiện tại.

Tra cứu và xem chi tiết không tạo địa điểm đã lưu hoặc lịch sử tra cứu thành phố.
Kết quả API thời tiết/Geocoding được ghi vào bảng cache riêng khi gọi API thành công.
Chỉ khi bấm **Lưu địa điểm** và xác nhận ghi chú, Client gửi `SAVE_LOCATION`
để lưu tên thành phố kèm quốc gia, tọa độ và ghi chú vào bảng địa điểm đã lưu.
Không cần sửa cấu trúc database cho thay đổi này.

Chạy `test_city_search.bat` để kiểm tra với HTTP server giả lập độc lập MySQL;
chạy `test_city_search.bat --live` để kiểm tra với API thật.

Tài liệu nguồn: [Open-Meteo Geocoding](https://open-meteo.com/en/docs/geocoding-api),
[OpenWeather Current Weather](https://openweathermap.org/current),
[Wikipedia/MediaWiki Geosearch](https://www.mediawiki.org/wiki/API:Geosearch).

## Cấu hình OpenWeather

OpenWeather kiểm tra bảng `bo_nho_dem_thoi_tiet` trước khi gọi API. Cache thời tiết
được phân biệt theo tọa độ và đơn vị metric; Geocoding dùng khóa tên đã chuẩn hóa.
TTL mặc định 5 phút, cấu hình bằng VM option `-Dweather.cache.ttlMinutes=10`
(giới hạn 5–10 phút). Cache còn hạn được dùng lại giữa các Client; hết hạn thì
gọi API và cập nhật bằng upsert. Lỗi API không được cache, cũng không trả cache
hết hạn khi API lỗi. Thời điểm dữ liệu gốc được giữ nguyên khi đọc cache.
Nút cập nhật vẫn dùng cache còn hiệu lực. Giao diện hiển thị nguồn cache/API.
Không cần sửa schema; sử dụng bảng cache hiện có, không phải `dia_diem_da_luu`.

Trên PowerShell, cấu hình key cho tiến trình Server rồi khởi động:

```powershell
$env:OPENWEATHER_API_KEY = "API_KEY_CUA_BAN"
.\run_server.bat
```

Cũng có thể dùng VM option `-Dopenweather.api.key=...` khi chạy `ServerMain`
trong IDE. Không ghi key trực tiếp vào mã nguồn. Khởi động lại Server sau khi
thay đổi cấu hình.

`GET_WEATHER` và dịch vụ theo dõi thời tiết gọi OpenWeather Direct Geocoding
để lấy `lat/lon`, rồi gọi Current Weather bằng tọa độ, không gửi tên thành phố
cho endpoint thời tiết. Nếu tên có nhiều kết quả, yêu cầu thêm mã quốc gia/tỉnh
(ví dụ `Paris,FR`). Khi chọn dòng trong bảng tra cứu, dùng ngay tọa độ đã được
Geocoding xác định để lấy thời tiết OpenWeather.

Không tự động trả dữ liệu giả hoặc chuyển sang nhà cung cấp khác khi lỗi.
Trong tab tra cứu thành phố, đồng hồ chạy mỗi giây theo múi giờ từ Geocoding.
Nút **Cập nhật thời tiết** lấy lại dữ liệu API; ứng dụng cũng tự cập nhật mỗi
5 phút khi đang xem tab. Hiển thị riêng thời điểm dữ liệu OpenWeather và lần
lấy dữ liệu gần nhất. Thời tiết là dữ liệu mới nhất nhà cung cấp trả về,
không phải luồng đo đạc cập nhật từng giây như đồng hồ.
Thiếu key, HTTP 401/403/429, lỗi mạng và dữ liệu không hợp lệ được báo rõ.
Dịch vụ theo dõi gửi `WEATHER_ERROR` đến Client đang kết nối và bỏ qua địa điểm
bị lỗi, tiếp tục kiểm tra các địa điểm còn lại. Phản hồi lỗi `GET_WEATHER` dùng
action `ERROR`; bảng tra cứu hiển thị lỗi thời tiết nhưng vẫn cho xem địa điểm.

Chạy `test_openweather.bat` để kiểm tra bằng HTTP server giả lập, không cần key
thật hoặc MySQL. Tài liệu: [OpenWeather Geocoding](https://openweathermap.org/api/geocoding-api).

## Tra cứu quốc gia

Tab **Tra cứu quốc gia** hiển thị 12 mục: tên quốc gia, thủ đô, vị trí/châu lục,
diện tích, dân số, ngôn ngữ, tiền tệ, quốc kỳ, thể chế chính trị, kinh tế/GDP,
khí hậu, văn hóa và du lịch. Nhập tên tiếng Việt, tiếng Anh hoặc mã ISO
(ví dụ `Việt Nam`, `Nhật Bản`, `Trung Quốc`, `VN`, `JP`, `CN`).

Server lấy thông tin cơ bản từ [countries.dev](https://countries.dev/docs);
không ưu tiên các bản ghi mẫu trong bảng quốc gia. Quốc kỳ được hiển thị bằng
ảnh PNG từ [Flagpedia/FlagCDN](https://flagpedia.net/download/api), tải ngoài
luồng giao diện và giữ đúng tỷ lệ. Khi thiếu ảnh hoặc tải lỗi, khung quốc kỳ
hiển thị trạng thái rõ ràng và không giữ ảnh của lần tìm trước.

GDP lấy qua [World Bank Indicators API](https://datahelpdesk.worldbank.org/knowledgebase/articles/898581-api-basic-call-structures),
dùng chỉ tiêu `NY.GDP.MKTP.CD`: GDP danh nghĩa theo USD hiện hành, ở năm gần nhất
có số liệu. Giao diện ghi cả năm và liên kết nguồn; đây là thống kê theo năm.
Chính trị, kinh tế, khí hậu, văn hóa và du lịch là các trích đoạn ngắn từ các
mục tương ứng của Wikipedia tiếng Việt, kèm nguồn để đọc toàn bộ bài viết.
Nội dung Wikipedia được ghi nguồn và tuân theo giấy phép CC BY-SA.
Khí hậu được lấy từ mục khí hậu của bài viết, không thay bằng thời tiết hiện tại.

Wikipedia và World Bank được gọi song song, giới hạn 7 giây mỗi nguồn và
7,5 giây tổng cho phần bổ sung. Client chờ tối đa 15 giây riêng cho
`SEARCH_COUNTRY` để đủ thời gian lấy metadata và phần bổ sung; các yêu cầu
khác vẫn chờ tối đa 10 giây. Metadata và phần bổ sung hoàn chỉnh được cache tối đa
24 giờ; phần bổ sung thiếu/lỗi được thử lại sau 1 phút. Dữ liệu thiếu được
ghi **Chưa có dữ liệu**; lỗi từng nguồn không làm mất các mục đã tra được.
Không tự tạo số liệu GDP hoặc mô tả thay thế khi API lỗi.
Chạy `test_country_search.bat` để kiểm tra 12 mục, nguồn dữ liệu, lỗi từng phần
và việc tải/đổi ảnh quốc kỳ bằng fixture cục bộ, không cần MySQL hay API key.

## So sánh thành phố và quốc gia

Mở tab **So sánh**, chọn **Hai thành phố** hoặc **Hai quốc gia**:

- Thành phố: nhập tên ở từng bên, bấm **Tìm**, chọn đúng thành phố/quốc gia
  từ danh sách, rồi bấm **So sánh**.
- Quốc gia: nhập hai tên tiếng Anh hoặc mã ISO (ví dụ `Vietnam`/`VN` và
  `Japan`/`JP`), rồi bấm **So sánh**.

Bảng đặt hai bên cạnh nhau, hiển thị dân số, tọa độ, nhiệt độ, độ ẩm, gió,
tiền tệ, ngôn ngữ và thủ đô. So sánh quốc gia có thêm diện tích, khu vực,
tiểu vùng, múi giờ và biên giới. Trong so sánh thành phố, tiền tệ/ngôn ngữ/thủ đô
được ghi rõ là thông tin của quốc gia chứa thành phố.

Thời tiết thành phố theo tọa độ đã chọn; thời tiết quốc gia lấy tại thủ đô
ghi trong bảng. Nếu API quốc gia không có tọa độ thủ đô, Server tra Geocoding
Open-Meteo theo tên thủ đô và mã quốc gia, ưu tiên địa điểm được xác nhận là
thủ đô, rồi gọi OpenWeather bằng tọa độ. Không chọn tùy ý địa điểm trùng tên.
Tọa độ tra được lưu trong bộ nhớ 24 giờ để dùng lại.
Chênh lệch số = bên trái trừ bên phải; chênh lệch độ ẩm tính
bằng điểm phần trăm. Dữ liệu thiếu hiển thị **Chưa có dữ liệu**, không thay bằng 0.
Lỗi thời tiết được ghi ngay trong bảng và ở phần thông báo bên dưới; các thông
tin còn lại vẫn xem được. Quốc gia được API xác nhận không có biên giới đất liền
hiển thị **Không có biên giới đất liền**. Nếu dữ liệu biên giới không có trong
phản hồi quốc gia, Server thử endpoint biên giới của cùng nguồn; lỗi tra cứu
không được coi là quốc gia không có biên giới.
Thời tiết cần key OpenWeather và dùng cache 5–10 phút đã cấu hình.
Thông tin quốc gia lấy từ [countries.dev](https://countries.dev/docs), không cần
API key quốc gia. Số liệu dân số/diện tích theo bộ dữ liệu của nguồn, không bảo
đảm là thống kê của năm hiện tại. Metadata quốc gia được cache trong bộ nhớ
Server tối đa 24 giờ; nếu tra biên giới lỗi, cache ngắn 1 phút để thử lại sớm.
API REST Countries v3 cũ đã ngừng phục vụ dữ liệu nên không dùng
endpoint đó cho tính năng mới.
Thao tác so sánh không tạo địa điểm yêu thích hay lịch sử tra cứu.
Chạy `test_comparison.bat` để kiểm tra so sánh với HTTP server giả lập và
kiểm tra bảng hiển thị, không cần MySQL hoặc API key thật.

## Tài khoản và phiên đăng nhập

### Gợi ý lịch trình du lịch

Tab **Lịch trình du lịch**: nhập địa điểm, chọn số ngày **1–7**, bấm **Tìm địa điểm**,
chọn đúng thành phố/quốc gia rồi bấm **Gợi ý lịch trình**. Server dùng tọa độ của
kết quả tra cứu để lấy thời tiết OpenWeather và địa điểm Wikipedia trong **10 km**
(khoảng cách đường thẳng từ tâm thành phố). `SEARCH_TRIP_CITIES` và `PLAN_TRIP`
yêu cầu đăng nhập; Client gửi ID thành phố đã chọn và `days`.

Lịch trình đơn giản ưu tiên điểm có khả năng trong nhà khi mưa/bão/tuyết hoặc
nhiệt độ từ 38 °C, ưu tiên ngoài trời khi điều kiện thuận lợi; sau đó xếp theo
khoảng cách và chia tối đa ba điểm mỗi ngày, không lặp điểm. Phân loại dựa trên
tên/mô tả, không xác nhận giờ mở cửa hay tuyến đường. Nguồn Wikipedia có thể có
bài viết chưa phù hợp tham quan; cần kiểm tra thông tin nguồn trước khi đi.
Thời tiết **hiện tại** không phải dự báo cho các ngày sau. Thiếu dữ liệu/API lỗi
được hiển thị rõ, không tạo địa điểm hoặc thời tiết giả. Máy Server cần Internet
và `OPENWEATHER_API_KEY`. Chạy `test_trip_planner.bat` để kiểm thử offline.

### Bản đồ thành phố

Tra cứu, chọn thành phố rồi mở tab **Bản đồ** để xem vị trí theo latitude/
longitude. Dấu đỏ là thành phố đang chọn; nút `+`/`−` thay đổi mức phóng đại.
**Mở bản đồ lớn** mở OpenStreetMap trong trình duyệt với marker tại cùng tọa độ.
Đổi thành phố tự cập nhật bản đồ, độc lập với việc tải thời tiết.

Client cần Internet để lấy tile. Tile chỉ được tải khi xem tab, ngoài luồng
Swing và được cache cục bộ ít nhất 7 ngày trong `map-cache/`, theo
[chính sách tile OpenStreetMap](https://operations.osmfoundation.org/policies/tiles/).
Giao diện luôn hiển thị attribution; lỗi Internet không làm dừng Client.
Chạy `test_map.bat` để kiểm tra phép chiếu tọa độ và link bản đồ bằng fixture offline.

Lưu lịch sử bằng `SAVE_HISTORY` với `{"keyword":"Việt Nam","type":"quoc_gia"}`
hoặc `{"keyword":"Hà Nội","type":"thanh_pho"}`. Server chỉ nhận hai loại này,
lưu keyword vào `tu_khoa_tim_kiem` và type vào `loai_tra_cuu` theo tài khoản
trong phiên. `GET_HISTORY` trả danh sách `{keyword,type}`; tab lịch sử hiển thị
loại trong cột riêng. Để chuyển dữ liệu quốc gia cũ có hậu tố ` (Quốc gia)`,
sao lưu rồi chạy `database/cap_nhat_lich_su.sql`; script không thêm/xóa bản ghi.

### Cấu trúc database và nâng cấp

Schema mới có UNIQUE cho địa điểm đã lưu theo tài khoản/tên/tọa độ và theo dõi
thời tiết theo tài khoản/địa điểm/điều kiện. Các cột tọa độ sinh tự động giúp chặn
trùng ngay cả khi tọa độ NULL. Lịch sử giữ các lần tra cứu lặp lại. Các index
theo tài khoản/thời gian hỗ trợ danh sách cá nhân; khách sạn có index thành phố/
đánh giá. B-tree không tối ưu tìm chuỗi có wildcard đầu (`LIKE '%...%'`).

Cache dùng `cache_key`, `data_json` (LONGTEXT), `type`, `created_at`, `expires_at`;
khóa cache UNIQUE phân biệt hoa/thường, có index hết hạn và loại/hết hạn.
`type` là `weather`, `geocoding` hoặc `generic`. Khi refresh, DAO cập nhật JSON,
loại và cả thời gian tạo/hết hạn; chỉ đọc bản ghi chưa hết hạn.

Database mới: import `database/tra_cuu_dia_ly.sql`. Database cũ: sao lưu,
dừng Server và chạy `database/cap_nhat_database.sql` **một lần** trước khi dùng
DAO mới. Script giữ dữ liệu cache cũ và dừng trước ALTER nếu địa điểm/theo dõi
bị trùng, điều kiện hoặc thời gian tạo cache NULL; xem và xử lý các bản ghi đó trước khi nâng cấp.
DDL không được rollback toàn bộ; nếu lỗi giữa chừng, kiểm tra schema đã áp dụng
trước khi chạy tiếp. Nếu procedure còn tồn tại sau lỗi preflight, xóa procedure
`migrate_database_v14` rồi chạy lại sau khi đã xử lý dữ liệu.
Kiểm thử schema trên instance dùng riêng (không dùng database làm việc):
`javac -d tests/bin tests/DatabaseSchemaTest.java` rồi chạy
`java -cp tests/bin DatabaseSchemaTest <mysql.exe> <port>`. Test tạo/reset
database `geo_schema_v14_test`. `CacheDAOIntegrationTest` kiểm tra DAO trên
database kiểm thử đó. Schema/migration đã được kiểm tra trên MariaDB 10.4 của XAMPP.

Server xử lý Client bằng thread pool với tối đa 64 worker. Đặt
`-Dserver.maxClients=32` khi chạy Java để thay đổi giới hạn. Kết nối vượt giới hạn
bị đóng ngay, không xếp hàng các phiên TCP kéo dài. Worker rảnh được tái sử dụng
và thu hồi sau 60 giây. Khi dừng JVM (Ctrl+C), shutdown hook đóng cổng lắng nghe,
dừng lịch theo dõi thời tiết và đóng socket Client để giải phóng luồng chờ đọc;
pool chờ tối đa 5 giây rồi yêu cầu ngắt các tác vụ còn lại.
Chạy `test_thread_pool.bat` để kiểm tra giới hạn kết nối và shutdown.

Trong tab theo dõi thời tiết, chọn một đăng ký rồi bấm **Hủy theo dõi**.
Client gửi `UNSUBSCRIBE_WEATHER` với `id` đăng ký; Server kiểm tra chủ sở hữu
bằng `currentUserId` và trả `UNSUBSCRIBE_WEATHER_RESPONSE` dạng boolean.
Server tiếp tục chủ động gửi `WEATHER_ALERT`/`WEATHER_ERROR`. Phản hồi và push
đều được gửi qua `ClientSession` với khóa đồng bộ chung, và `SecureChannel`
đồng bộ quá trình mã hóa, đánh số và ghi thông điệp.

Tab **Tài khoản** hiển thị tên đăng nhập, họ tên và email. Dùng **Lưu thay đổi**
để cập nhật họ tên/email, **Tải lại thông tin** để đọc lại hồ sơ từ Server.
Đổi mật khẩu yêu cầu mật khẩu hiện tại và xác nhận mật khẩu mới (8–128 ký tự).
Server kiểm tra mật khẩu hiện tại, lưu mật khẩu mới bằng PBKDF2 có salt riêng;
không gửi mật khẩu hoặc hash trong hồ sơ trả về Client.
**Đăng xuất** đóng kết nối cũ và trở về màn hình đăng nhập.

Chỉ `LOGIN` và `REGISTER` được dùng khi chưa đăng nhập. Mọi thao tác khác,
kể cả tra cứu, so sánh, thời tiết và `LOGOUT`, phải có `currentUserId`;
nếu chưa đăng nhập, Server trả `UNAUTHORIZED` trước khi xử lý dữ liệu.

Mỗi `ClientHandler` giữ `currentUserId` sau khi đăng nhập thành công. Các thao tác
`GET_PROFILE`, `UPDATE_PROFILE`, `CHANGE_PASSWORD`, `SAVE_LOCATION`,
`GET_SAVED_LOCATIONS`, `DELETE_LOCATION`, `SAVE_HISTORY`, `GET_HISTORY`,
`SUBSCRIBE_WEATHER` và `GET_SUBSCRIPTIONS` yêu cầu đăng nhập và chỉ dùng ID này.
Server bỏ qua `userId`/ID tài khoản do Client gửi; Client không cần gửi chúng.
Riêng `DELETE_LOCATION` vẫn nhận `id` của **bản ghi địa điểm** và kiểm tra bản ghi
thuộc tài khoản trong phiên trước khi xóa.

Đăng xuất, ngắt TCP hoặc bắt đầu một lần đăng nhập mới đều xóa thông tin phiên
cũ. Đăng nhập thất bại không giữ quyền tài khoản trước đó. Cảnh báo thời tiết
cũng kiểm tra tài khoản còn gắn với kết nối tại thời điểm gửi.
Chạy `test_account.bat` để kiểm tra giao diện tài khoản, giả mạo ID, các phiên
độc lập và vòng đời kết nối bảo mật bằng dữ liệu giả lập, không cần MySQL.

## Kiểm tra dữ liệu đầu vào

Client kiểm tra ngay tại biểu mẫu và trước khi gửi TCP. Server tự kiểm tra lại
kiểu JSON và từng trường trước khi gọi API hoặc DAO, kể cả yêu cầu gửi trực tiếp.
Thông báo lỗi ghi rõ trường sai bằng tiếng Việt. Hai phía dùng cùng quy tắc:

| Trường | Quy tắc |
| --- | --- |
| Tên đăng nhập | 1–50 ký tự, không rỗng, không chứa khoảng trắng hoặc ký tự điều khiển |
| Mật khẩu đăng nhập/đăng ký/đổi mật khẩu | 8–128 ký tự, không chỉ gồm khoảng trắng; giữ nguyên khoảng trắng có ý nghĩa |
| Họ tên | 1–100 ký tự, không chứa ký tự điều khiển |
| Email | Tối đa 100 ký tự, có phần tên và tên miền hợp lệ, ví dụ `ten@example.com` |
| Tên thành phố / quốc gia | Thành phố 2–200 ký tự, quốc gia 1–200 ký tự |
| Lưu địa điểm / lịch sử / ghi chú | Tên địa điểm và lịch sử tối đa 255 ký tự; ghi chú tối đa 2000 ký tự |
| Vĩ độ / kinh độ | Số hữu hạn trong [-90, 90] / [-180, 180], không nhận chuỗi số, NaN hoặc Infinity |
| Mã bản ghi địa điểm/thành phố | Số nguyên dương trong phạm vi Java `int`, không làm tròn số phân số |

Giới hạn độ dài tính theo ký tự Unicode. Lưu địa điểm yêu cầu đủ cả hai tọa độ;
tọa độ `(0, 0)` thật vẫn hợp lệ. Theo dõi thời tiết bằng tên thành phố được phép
thiếu cả hai tọa độ và lưu SQL `NULL`; nếu cung cấp thì phải đủ cặp và hợp lệ.
ID tài khoản vẫn lấy từ phiên Server, các ID giả mạo của Client bị bỏ qua.

Chạy `test_input_validation.bat` để kiểm tra các đầu vào sai, ranh giới tọa độ,
kiểu JSON, cách ly phiên, giao diện tài khoản và TCP mã hóa bằng fixture offline.
Kiểm thử không thay đổi MySQL và không yêu cầu API key.

## Tài khoản mẫu

Database có sẵn một số tài khoản để kiểm tra:

| Tên đăng nhập | Mật khẩu |
| ------------- | -------- |
| `nguyenvana`  | `Demo12345!` |
| `phunggiahuy` | `Demo12345!` |
| `tranvanb`    | `Demo12345!` |

Mật khẩu mẫu được lưu bằng PBKDF2 có salt riêng cho từng tài khoản.
Nếu đã tạo database với mật khẩu mẫu `123456`, chạy riêng
[database/cap_nhat_mat_khau_mau.sql](database/cap_nhat_mat_khau_mau.sql)
trong phpMyAdmin để chuyển sang mật khẩu mới và giữ lịch sử/địa điểm đã lưu.
Script chỉ cập nhật mật khẩu mẫu cũ; mật khẩu đã đổi được giữ nguyên.

## Xử lý lỗi thường gặp

- **Không kết nối được MySQL**: kiểm tra MySQL trong XAMPP đã chạy, đúng port, tên database và thông tin đăng nhập.
- **Không tìm thấy driver JDBC**: kiểm tra `mysql-connector-j` đã được thêm vào classpath của Server.
- **Client không kết nối được Server**: khởi động Server trước, kiểm tra host/port ở cả hai phía.
- **Port đã được sử dụng**: đổi port trong Server và cập nhật lại port tương ứng trong Client.
