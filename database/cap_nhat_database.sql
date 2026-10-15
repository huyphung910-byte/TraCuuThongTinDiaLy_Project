-- Run once against the previous schema, with Server stopped. Does not drop data.
USE `tra_cuu_dia_ly`;
DELIMITER $$
CREATE PROCEDURE migrate_database_v14()
BEGIN
    -- Check all data before any ALTER; duplicates must be reviewed, never deleted automatically.
    IF EXISTS (
        SELECT 1 FROM dia_diem_da_luu
        GROUP BY id_tai_khoan, ten_dia_diem, IFNULL(vi_do, 99), IFNULL(kinh_do, 999)
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate saved locations: review before migration';
    END IF;
    IF EXISTS (
        SELECT 1 FROM theo_doi_thoi_tiet
        GROUP BY id_tai_khoan, ten_dia_diem, dieu_kien_canh_bao HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate weather subscriptions: review before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM theo_doi_thoi_tiet WHERE dieu_kien_canh_bao IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Null alert conditions: review before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM bo_nho_dem_thoi_tiet WHERE thoi_gian_tao IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Null cache creation times: review before migration';
    END IF;

    ALTER TABLE dia_diem_da_luu
        ADD COLUMN vi_do_key DECIMAL(10, 8) GENERATED ALWAYS AS (IFNULL(vi_do, 99)) STORED,
        ADD COLUMN kinh_do_key DECIMAL(11, 8) GENERATED ALWAYS AS (IFNULL(kinh_do, 999)) STORED,
        ADD UNIQUE KEY uq_saved_location (id_tai_khoan, ten_dia_diem, vi_do_key, kinh_do_key),
        ADD KEY idx_saved_user_date (id_tai_khoan, ngay_luu);
    ALTER TABLE theo_doi_thoi_tiet
        MODIFY dieu_kien_canh_bao VARCHAR(200) NOT NULL DEFAULT 'thoi_tiet_xau',
        ADD UNIQUE KEY uq_weather_subscription (id_tai_khoan, ten_dia_diem, dieu_kien_canh_bao),
        ADD KEY idx_subscription_user_date (id_tai_khoan, ngay_dang_ky);
    ALTER TABLE lich_su_tra_cuu ADD KEY idx_history_user_date (id_tai_khoan, thoi_gian_tra_cuu);
    ALTER TABLE khach_san ADD KEY idx_hotel_city_rating (ten_thanh_pho, danh_gia);
    ALTER TABLE bo_nho_dem_thoi_tiet
        CHANGE khoa_bo_nho_dem cache_key VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
        CHANGE du_lieu_json data_json LONGTEXT NOT NULL,
        CHANGE thoi_gian_tao created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CHANGE thoi_gian_het_han expires_at DATETIME NOT NULL,
        ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'generic' AFTER data_json,
        ADD KEY idx_cache_expires (expires_at),
        ADD KEY idx_cache_type_expires (type, expires_at);
    UPDATE bo_nho_dem_thoi_tiet SET type = CASE
        WHEN cache_key LIKE 'openweather:geo:%' THEN 'geocoding'
        WHEN cache_key LIKE 'openweather:weather:%' THEN 'weather'
        ELSE 'generic' END;
END$$
DELIMITER ;
CALL migrate_database_v14();
DROP PROCEDURE migrate_database_v14;
