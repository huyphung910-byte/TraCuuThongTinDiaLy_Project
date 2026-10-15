USE `tra_cuu_dia_ly`;
-- Chuyển mật khẩu mẫu cũ 123456 thành Demo12345! (đủ tối thiểu 8 ký tự).
-- Chỉ khớp văn bản mẫu cũ hoặc đúng hash trong bản khởi tạo trước đây.
-- Không ghi đè mật khẩu khác, không xóa lịch sử hay địa điểm đã lưu; chạy lại an toàn.
START TRANSACTION;
UPDATE `tai_khoan` SET `mat_khau` = '310000:q3uNpsnHsV8Uz5ZdQJd/Qg==:+JAs9eBmgvPfV8rvaID7e0gmrCWJsNPuQdwkUoQfgUM='
WHERE `ten_dang_nhap` = 'nguyenvana' AND (BINARY `mat_khau` = '123456' OR BINARY `mat_khau` = '310000:QNh/co/N5L1vvOuqc3ssQw==:rUvgXeDR28JKZG6F1twoIH9ydBWTbNO1pYe3FRYGw/4=');
UPDATE `tai_khoan` SET `mat_khau` = '310000:HvLwaE1fQjszBzCaC7Ij+g==:x7SVPRAKn6o/bcZoxhkWa08sM0Dwsf8eIHZgNA+al4E='
WHERE `ten_dang_nhap` = 'phunggiahuy' AND (BINARY `mat_khau` = '123456' OR BINARY `mat_khau` = '310000:rC3tjM8zAetJlw27Uw+8TA==:PoKAz4XNbChL0XveYh8g9q8PrG1Dp/D3UN7XnCYke1A=');
UPDATE `tai_khoan` SET `mat_khau` = '310000:/PIb6HFDCOW+aWjEmG6Epw==:prGcg8rzqs+nSNM9C5VHfafaQIRRK4rJvParAFir6zs='
WHERE `ten_dang_nhap` = 'tranvanb' AND (BINARY `mat_khau` = '123456' OR BINARY `mat_khau` = '310000:1TX3o2BR4mf1BbvJQGV+eg==:SBz4UJxVsbR8Hnf8LX8QqcgYVahGVxW1D9p5wmrzgD0=');
COMMIT;
