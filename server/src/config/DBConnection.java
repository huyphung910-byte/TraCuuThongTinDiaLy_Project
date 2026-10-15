package config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.IOException;
import util.AppConfig;

public class DBConnection {
    public static Connection getConnection() throws SQLException {
        try {
            AppConfig config = AppConfig.load("server");
            String url = "jdbc:mysql://" + config.required("db.host") + ":"
                    + config.positiveInt("db.port", 65535) + "/" + config.required("db.name")
                    + "?useSSL=false&useUnicode=true&characterEncoding=UTF-8&connectTimeout=5000&socketTimeout=10000";
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(url, config.required("db.user"), config.get("db.password"));
        } catch (ClassNotFoundException e) {
            throw new SQLException("Thiếu MySQL Connector/J trong server/libs.", "08001", e);
        } catch (IOException e) {
            throw new SQLException("Không đọc được cấu hình MySQL: " + e.getMessage(), "08001", e);
        } catch (SQLException e) {
            throw new SQLException("Không kết nối được MySQL. Kiểm tra dịch vụ MySQL và db.host/db.port/db.name/db.user/db.password trong server/config.properties.",
                    e.getSQLState(), e.getErrorCode(), e);
        }
    }
}
