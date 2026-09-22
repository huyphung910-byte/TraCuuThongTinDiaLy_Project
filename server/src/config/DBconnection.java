package config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    // Cấu hình Database cho dự án mới
    private static final String HOST = "localhost";
    private static final String PORT = "3306";
    private static final String DB_NAME = "tra_cuu_dia_ly"; // <-- Đã đổi tên Database mới
    private static final String USER = "root";
    private static final String PASS = "";
    private static final String URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB_NAME + "?useSSL=false&useUnicode=true&characterEncoding=UTF-8";

    public static Connection getConnection() {
        Connection conn = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(URL, USER, PASS);
            System.out.println("Kết nối CSDL thành công!");
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
        return conn;
    }
}