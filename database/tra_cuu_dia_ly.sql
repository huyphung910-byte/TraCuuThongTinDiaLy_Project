CREATE DATABASE IF NOT EXISTS `tra_cuu_dia_ly` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `tra_cuu_dia_ly`;

DROP TABLE IF EXISTS `theo_doi_thoi_tiet`;
DROP TABLE IF EXISTS `bo_nho_dem_thoi_tiet`;
DROP TABLE IF EXISTS `lich_su_tra_cuu`;
DROP TABLE IF EXISTS `dia_diem_da_luu`;
DROP TABLE IF EXISTS `tai_khoan`;

CREATE TABLE `tai_khoan` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `ten_dang_nhap` VARCHAR(50) NOT NULL UNIQUE,
    `mat_khau` VARCHAR(255) NOT NULL,
    `email` VARCHAR(100) UNIQUE,
    `ho_ten` VARCHAR(100),
    `ngay_tao` DATETIME DEFAULT CURRENT_TIMESTAMP
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
    `dieu_kien_canh_bao` VARCHAR(100) DEFAULT 'thoi_tiet_xau',
    `ngay_dang_ky` DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`id_tai_khoan`) REFERENCES `tai_khoan`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `tai_khoan` (`id`, `ten_dang_nhap`, `mat_khau`, `email`, `ho_ten`) VALUES
(1, 'nguyenvana', '123456', 'nguyenvana@gmail.com', 'Nguyễn Văn A'),
(2, 'phunggiahuy', '123456', 'phunggiahuy@gmail.com', 'Phùng Gia Huy'),
(3, 'tranvanb', '123456', 'tranvanb@gmail.com', 'Trần Văn B');

INSERT INTO `dia_diem_da_luu` (`id_tai_khoan`, `ten_dia_diem`, `vi_do`, `kinh_do`, `ghi_chu`) VALUES
(1, 'Thành phố Hồ Chí Minh', 10.82310000, 106.62970000, 'Nơi làm việc'),
(1, 'Hà Nội', 21.02850000, 105.85420000, 'Đi du lịch mùa thu'),
(2, 'Đà Nẵng', 16.05440000, 108.20220000, 'Thành phố yêu thích'),
(3, 'Tokyo', 35.67620000, 139.65030000, 'Chuyến công tác tiếp theo');

INSERT INTO `lich_su_tra_cuu` (`id_tai_khoan`, `tu_khoa_tim_kiem`, `loai_tra_cuu`) VALUES
(1, 'Thành phố Hồ Chí Minh', 'thanh_pho'),
(1, 'Việt Nam', 'quoc_gia'),
(2, 'Đà Nẵng', 'thanh_pho'),
(2, 'Nhật Bản', 'quoc_gia'),
(3, 'Tokyo', 'thanh_pho');

INSERT INTO `bo_nho_dem_thoi_tiet` (`khoa_bo_nho_dem`, `du_lieu_json`, `thoi_gian_het_han`) VALUES
('thoitiet_hcm', '{"thanh_pho": "TP.HCM", "nhiet_do": 32, "do_am": 75, "thoi_tiet": "Nắng nhẹ"}', '2026-12-31 23:59:59'),
('thoitiet_hanoi', '{"thanh_pho": "Hà Nội", "nhiet_do": 26, "do_am": 80, "thoi_tiet": "Mây rải rác"}', '2026-12-31 23:59:59'),
('thoitiet_danang', '{"thanh_pho": "Đà Nẵng", "nhiet_do": 29, "do_am": 70, "thoi_tiet": "Trong xanh"}', '2026-12-31 23:59:59');

INSERT INTO `theo_doi_thoi_tiet` (`id_tai_khoan`, `ten_dia_diem`, `vi_do`, `kinh_do`, `dieu_kien_canh_bao`) VALUES
(1, 'Thành phố Hồ Chí Minh', 10.82310000, 106.62970000, 'Mưa lớn & Bão'),
(2, 'Đà Nẵng', 16.05440000, 108.20220000, 'Nhiệt độ trên 38 độ C'),
(3, 'Tokyo', 35.67620000, 139.65030000, 'Tuyết rơi nặng hạt');