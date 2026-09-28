CREATE DATABASE IF NOT EXISTS `tra_cuu_dia_ly` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `tra_cuu_dia_ly`;

DROP TABLE IF EXISTS `theo_doi_thoi_tiet`;
DROP TABLE IF EXISTS `bo_nho_dem_thoi_tiet`;
DROP TABLE IF EXISTS `lich_su_tra_cuu`;
DROP TABLE IF EXISTS `khach_san`;
DROP TABLE IF EXISTS `dia_diem_da_luu`;
DROP TABLE IF EXISTS `quoc_gia`;
DROP TABLE IF EXISTS `tai_khoan`;

CREATE TABLE `tai_khoan` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `ten_dang_nhap` VARCHAR(50) NOT NULL UNIQUE,
    `mat_khau` VARCHAR(255) NOT NULL,
    `email` VARCHAR(100) UNIQUE,
    `ho_ten` VARCHAR(100),
    `ngay_tao` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `quoc_gia` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `ten_quoc_gia` VARCHAR(100) NOT NULL,
    `ma_quoc_gia` VARCHAR(10) NOT NULL UNIQUE,
    `thu_do` VARCHAR(100),
    `don_vi_tien_te` VARCHAR(100),
    `ngon_ngu` VARCHAR(200),
    `url_quoc_ky` VARCHAR(500),
    `quoc_gia_lien_ke` TEXT,
    `diem_du_lich_noi_bat` TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `dia_diem_da_luu` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `id_tai_khoan` INT NOT NULL,
    `ten_dia_diem` VARCHAR(255) NOT NULL,
    `vi_do` DECIMAL(10, 8),
    `kinh_do` DECIMAL(11, 8),
    `ghi_chu` TEXT,
    `ngay_luu` DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`id_tai_khoan`) REFERENCES `tai_khoan`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `khach_san` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `ten_khach_san` VARCHAR(255) NOT NULL,
    `ten_thanh_pho` VARCHAR(100) NOT NULL,
    `dia_chi` VARCHAR(500),
    `gia_mot_dem` DECIMAL(10, 2),
    `don_vi_tien` VARCHAR(10) DEFAULT 'VND',
    `danh_gia` DECIMAL(2, 1),
    `mo_ta` TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `lich_su_tra_cuu` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `id_tai_khoan` INT NOT NULL,
    `tu_khoa_tim_kiem` VARCHAR(255) NOT NULL,
    `loai_tra_cuu` ENUM('thanh_pho', 'quoc_gia') DEFAULT 'thanh_pho',
    `thoi_gian_tra_cuu` DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`id_tai_khoan`) REFERENCES `tai_khoan`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `bo_nho_dem_thoi_tiet` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `khoa_bo_nho_dem` VARCHAR(255) NOT NULL UNIQUE,
    `du_lieu_json` LONGTEXT NOT NULL,
    `thoi_gian_tao` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `thoi_gian_het_han` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `theo_doi_thoi_tiet` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `id_tai_khoan` INT NOT NULL,
    `ten_dia_diem` VARCHAR(255) NOT NULL,
    `vi_do` DECIMAL(10, 8),
    `kinh_do` DECIMAL(11, 8),
    `dieu_kien_canh_bao` VARCHAR(200) DEFAULT 'thoi_tiet_xau',
    `ngay_dang_ky` DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`id_tai_khoan`) REFERENCES `tai_khoan`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================== DU LIEU MAU =====================

INSERT INTO `tai_khoan` (`id`, `ten_dang_nhap`, `mat_khau`, `email`, `ho_ten`) VALUES
(1, 'nguyenvana', '123456', 'nguyenvana@gmail.com', 'Nguyen Van A'),
(2, 'phunggiahuy', '123456', 'phunggiahuy@gmail.com', 'Phung Gia Huy'),
(3, 'tranvanb', '123456', 'tranvanb@gmail.com', 'Tran Van B');

INSERT INTO `quoc_gia` (`ten_quoc_gia`, `ma_quoc_gia`, `thu_do`, `don_vi_tien_te`, `ngon_ngu`, `url_quoc_ky`, `quoc_gia_lien_ke`, `diem_du_lich_noi_bat`) VALUES
('Viet Nam', 'VN', 'Ha Noi', 'Dong Viet Nam (VND)', 'Tieng Viet', 'https://flagcdn.com/w320/vn.png', 'Trung Quoc, Lao, Campuchia', 'Ha Long Bay, Hoi An, Sapa, Nha Trang, Phu Quoc'),
('Nhat Ban', 'JP', 'Tokyo', 'Yen Nhat (JPY)', 'Tieng Nhat', 'https://flagcdn.com/w320/jp.png', 'Han Quoc, Trung Quoc (bien)', 'Tokyo, Kyoto, Osaka, Fuji, Hiroshima'),
('Han Quoc', 'KR', 'Seoul', 'Won Han Quoc (KRW)', 'Tieng Han', 'https://flagcdn.com/w320/kr.png', 'Nhat Ban (bien), Trung Quoc (bien), Trieu Tien', 'Seoul, Jeju, Busan, Gyeongju'),
('Thai Lan', 'TH', 'Bangkok', 'Baht Thai (THB)', 'Tieng Thai', 'https://flagcdn.com/w320/th.png', 'Myanma, Lao, Campuchia, Malaysia', 'Bangkok, Phuket, Chiang Mai, Pattaya'),
('Phap', 'FR', 'Paris', 'Euro (EUR)', 'Tieng Phap', 'https://flagcdn.com/w320/fr.png', 'Duc, Tay Ban Nha, Y, Bi, Thuy Si', 'Paris (Eiffel), Nice, Lyon, Bordeaux'),
('My', 'US', 'Washington D.C.', 'Do la My (USD)', 'Tieng Anh', 'https://flagcdn.com/w320/us.png', 'Canada, Mexico', 'New York, Los Angeles, Las Vegas, Miami, Hawaii'),
('Trung Quoc', 'CN', 'Bac Kinh', 'Nhan dan te (CNY)', 'Tieng Trung', 'https://flagcdn.com/w320/cn.png', 'Nga, Mong Co, Viet Nam, An Do, Kazakhstan', 'Vat Thanh Co Cung, Truong Thanh, Thuong Hai'),
('Singapore', 'SG', 'Singapore', 'Do la Singapore (SGD)', 'Tieng Anh, Malay, Trung, Tamil', 'https://flagcdn.com/w320/sg.png', 'Malaysia, Indonesia (bien)', 'Marina Bay Sands, Gardens by the Bay, Sentosa');

INSERT INTO `khach_san` (`ten_khach_san`, `ten_thanh_pho`, `dia_chi`, `gia_mot_dem`, `don_vi_tien`, `danh_gia`, `mo_ta`) VALUES
('Rex Hotel Saigon', 'Ho Chi Minh', '141 Nguyen Hue, Quan 1', 2500000, 'VND', 4.5, 'Khach san 5 sao lung danh giua trung tam TP.HCM'),
('Caravelle Saigon', 'Ho Chi Minh', '19 Lam Son, Quan 1', 3200000, 'VND', 4.7, 'Khach san sang trong nhin ra quang truong Lam Son'),
('Sofitel Legend Metropole', 'Ha Noi', '15 Ngo Quyen, Hoan Kiem', 4500000, 'VND', 4.8, 'Bieu tuong lich su cua Ha Noi, phong cach Phap co dien'),
('Hilton Hanoi Opera', 'Ha Noi', '1 Le Thanh Tong, Hoan Kiem', 3000000, 'VND', 4.6, 'Nhin ra Nha Hat Lon, dich vu dang cap quoc te'),
('Fusion Maia Da Nang', 'Da Nang', 'Truong Sa, Quan Ngu Hanh Son', 3800000, 'VND', 4.9, 'Resort spa doc dao sat bien, moi phong co ho boi rieng'),
('Mercure Da Nang', 'Da Nang', '170 Tran Phu, Quan Hai Chau', 1200000, 'VND', 4.2, 'Khach san tien nghi, vi tri trung tam Da Nang'),
('Park Hyatt Tokyo', 'Tokyo', 'Shinjuku 3-7-1-2', 5500000, 'VND', 4.8, 'Khach san hang dau Tokyo trong phim Lost in Translation'),
('Shinjuku Granbell Hotel', 'Tokyo', 'Kabukicho 2-14-5', 1800000, 'VND', 4.3, 'Khach san hien dai o khu Shinjuku nang dong'),
('The Ritz-Carlton Seoul', 'Seoul', '120 Namdaemun-ro, Jung-gu', 4200000, 'VND', 4.7, 'Sang trong giua long Seoul, gan Myeongdong'),
('Mandarin Oriental Bangkok', 'Bangkok', '48 Oriental Avenue', 5000000, 'VND', 4.9, 'Khach san huyen thoai ben song Chao Phraya'),
('Marina Bay Sands', 'Singapore', '10 Bayfront Avenue', 8000000, 'VND', 4.6, 'Bieu tuong Singapore voi ho boi tren may');

INSERT INTO `dia_diem_da_luu` (`id_tai_khoan`, `ten_dia_diem`, `vi_do`, `kinh_do`, `ghi_chu`) VALUES
(1, 'Thanh pho Ho Chi Minh', 10.82310000, 106.62970000, 'Noi lam viec'),
(1, 'Ha Noi', 21.02850000, 105.85420000, 'Di du lich mua thu'),
(2, 'Da Nang', 16.05440000, 108.20220000, 'Thanh pho yeu thich'),
(3, 'Tokyo', 35.67620000, 139.65030000, 'Chuyen cong tac tiep theo');

INSERT INTO `lich_su_tra_cuu` (`id_tai_khoan`, `tu_khoa_tim_kiem`, `loai_tra_cuu`) VALUES
(1, 'Ho Chi Minh', 'thanh_pho'),
(1, 'Viet Nam', 'quoc_gia'),
(2, 'Da Nang', 'thanh_pho'),
(2, 'Nhat Ban', 'quoc_gia'),
(3, 'Tokyo', 'thanh_pho');

INSERT INTO `theo_doi_thoi_tiet` (`id_tai_khoan`, `ten_dia_diem`, `vi_do`, `kinh_do`, `dieu_kien_canh_bao`) VALUES
(1, 'Ho Chi Minh', 10.82310000, 106.62970000, 'Mua lon va Bao'),
(2, 'Da Nang', 16.05440000, 108.20220000, 'Nhiet do tren 38 do C'),
(3, 'Tokyo', 35.67620000, 139.65030000, 'Tuyet roi nang hat');