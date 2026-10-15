import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Run only against a disposable MySQL/MariaDB instance: DatabaseSchemaTest MYSQL_EXE PORT. */
public final class DatabaseSchemaTest {
    private static final String SCHEMA = "geo_schema_v14_test";

    private DatabaseSchemaTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: DatabaseSchemaTest MYSQL_EXE PORT");
        }

        Path root = findProjectRoot();
        List<String> command = List.of(
                args[0], "--no-defaults", "--protocol=TCP", "--host=127.0.0.1",
                "--port=" + args[1], "--user=root", "--default-character-set=utf8mb4",
                "--batch", "--skip-column-names");

        String fresh = Files.readString(root.resolve("database/tra_cuu_dia_ly.sql"), StandardCharsets.UTF_8)
                .replace("`tra_cuu_dia_ly`", "`" + SCHEMA + "`");
        String migration = Files.readString(root.resolve("database/cap_nhat_database.sql"), StandardCharsets.UTF_8)
                .replace("`tra_cuu_dia_ly`", "`" + SCHEMA + "`");
        sql(command, fresh, null);

        String prefix = "USE " + SCHEMA + ";\n";
        sql(command, prefix + "INSERT INTO dia_diem_da_luu (id_tai_khoan,ten_dia_diem,vi_do,kinh_do) VALUES (1,'Null city',NULL,NULL);", null);
        sql(command, prefix + "INSERT INTO dia_diem_da_luu (id_tai_khoan,ten_dia_diem,vi_do,kinh_do) VALUES (1,'Null city',NULL,NULL);", "1062");
        sql(command, prefix + "INSERT INTO theo_doi_thoi_tiet (id_tai_khoan,ten_dia_diem,dieu_kien_canh_bao) VALUES (1,'Ho Chi Minh','Mua lon va Bao');", "1062");
        sql(command, prefix + "INSERT INTO theo_doi_thoi_tiet (id_tai_khoan,ten_dia_diem,dieu_kien_canh_bao) VALUES (2,'Ho Chi Minh','Mua lon va Bao');", null);
        sql(command, prefix + """
                INSERT INTO bo_nho_dem_thoi_tiet (cache_key,data_json,type,created_at,expires_at)
                VALUES ('weather:key','{}','weather',NOW(),DATE_ADD(NOW(),INTERVAL 5 MINUTE));
                INSERT INTO bo_nho_dem_thoi_tiet (cache_key,data_json,type,created_at,expires_at)
                VALUES ('weather:key','{"temp":32}','weather',NOW(),DATE_ADD(NOW(),INTERVAL 10 MINUTE))
                ON DUPLICATE KEY UPDATE data_json=VALUES(data_json),created_at=VALUES(created_at),expires_at=VALUES(expires_at);
                """, null);
        check(sql(command, prefix + "SELECT COUNT(*),data_json FROM bo_nho_dem_thoi_tiet WHERE cache_key='weather:key';", null)
                .equals("1\t{\"temp\":32}"));
        sql(command, prefix + "UPDATE bo_nho_dem_thoi_tiet SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE);", null);
        check(sql(command, prefix + "SELECT COUNT(*) FROM bo_nho_dem_thoi_tiet WHERE expires_at > NOW();", null)
                .equals("0"));

        // Reconstruct the previous schema in the disposable DB, preserving rows for migration checks.
        sql(command, prefix + """
                ALTER TABLE dia_diem_da_luu ADD INDEX legacy_saved_owner (id_tai_khoan);
                ALTER TABLE dia_diem_da_luu DROP INDEX uq_saved_location, DROP INDEX idx_saved_user_date,
                 DROP COLUMN vi_do_key, DROP COLUMN kinh_do_key;
                ALTER TABLE theo_doi_thoi_tiet ADD INDEX legacy_subscription_owner (id_tai_khoan);
                ALTER TABLE theo_doi_thoi_tiet DROP INDEX uq_weather_subscription, DROP INDEX idx_subscription_user_date,
                 MODIFY dieu_kien_canh_bao VARCHAR(200) DEFAULT 'thoi_tiet_xau';
                ALTER TABLE lich_su_tra_cuu ADD INDEX legacy_history_owner (id_tai_khoan);
                ALTER TABLE lich_su_tra_cuu DROP INDEX idx_history_user_date;
                ALTER TABLE khach_san DROP INDEX idx_hotel_city_rating;
                ALTER TABLE bo_nho_dem_thoi_tiet DROP INDEX idx_cache_expires, DROP INDEX idx_cache_type_expires,
                 DROP COLUMN type,
                 CHANGE cache_key khoa_bo_nho_dem VARCHAR(255) NOT NULL,
                 CHANGE data_json du_lieu_json LONGTEXT NOT NULL,
                 CHANGE created_at thoi_gian_tao DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                 CHANGE expires_at thoi_gian_het_han DATETIME NOT NULL;
                INSERT INTO dia_diem_da_luu (id_tai_khoan,ten_dia_diem) VALUES (1,'Null city');
                """, null);
        sql(command, migration, "Duplicate saved locations");
        check(sql(command, prefix + "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='vi_do_key';", null)
                .equals("0"));
        sql(command, prefix + """
                DROP PROCEDURE migrate_database_v14;
                DELETE FROM dia_diem_da_luu WHERE id=(SELECT newest FROM (SELECT MAX(id) newest FROM dia_diem_da_luu WHERE ten_dia_diem='Null city') t);
                """, null);
        sql(command, migration, null);
        check(sql(command, prefix + "SELECT data_json,type FROM bo_nho_dem_thoi_tiet WHERE cache_key='weather:key';", null)
                .equals("{\"temp\":32}\tgeneric"));
        check(sql(command, prefix + "SELECT COUNT(*) FROM dia_diem_da_luu;", null).equals("5"));
        System.out.println("Database schema test passed: uniqueness, NULL coordinates, expiry, upsert, migration and duplicate preflight.");
    }

    private static String sql(List<String> command, String statement, String expectedError) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(new ArrayList<>(command)).start();
        try (var input = process.getOutputStream()) {
            input.write(statement.getBytes(StandardCharsets.UTF_8));
        }
        String output;
        String error;
        try (var stdout = process.getInputStream(); var stderr = process.getErrorStream()) {
            output = new String(stdout.readAllBytes(), StandardCharsets.UTF_8).strip();
            error = new String(stderr.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (expectedError != null) {
            check(exitCode != 0 && error.contains(expectedError),
                    "Expected MySQL error " + expectedError + ", got: " + error);
        } else {
            check(exitCode == 0, "MySQL command failed: " + error);
        }
        return output;
    }

    private static Path findProjectRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("database/tra_cuu_dia_ly.sql"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("Could not find project root containing database/tra_cuu_dia_ly.sql");
    }

    private static void check(boolean condition) {
        check(condition, "Database schema test assertion failed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
