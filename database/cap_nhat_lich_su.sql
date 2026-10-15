-- Run once to normalize legacy country history written by the previous Client.
USE `tra_cuu_dia_ly`;
UPDATE lich_su_tra_cuu
SET loai_tra_cuu = 'quoc_gia',
    tu_khoa_tim_kiem = LEFT(tu_khoa_tim_kiem,
        CHAR_LENGTH(tu_khoa_tim_kiem) - CHAR_LENGTH(' (Quốc gia)'))
WHERE BINARY RIGHT(tu_khoa_tim_kiem, CHAR_LENGTH(' (Quốc gia)')) = BINARY ' (Quốc gia)';
