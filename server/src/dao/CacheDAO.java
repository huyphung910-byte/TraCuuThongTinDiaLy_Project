package dao;

import config.DBConnection;

import java.sql.*;

public class CacheDAO {
    private static final String GET_SQL =
            "SELECT data_json FROM bo_nho_dem_thoi_tiet " +
            "WHERE cache_key = ? AND expires_at > NOW()";
    private static final String UPSERT_SQL =
            "INSERT INTO bo_nho_dem_thoi_tiet (cache_key, data_json, type, created_at, expires_at) " +
            "VALUES (?, ?, ?, NOW(), DATE_ADD(NOW(), INTERVAL ? MINUTE)) " +
            "ON DUPLICATE KEY UPDATE data_json = VALUES(data_json), type = VALUES(type), " +
            "created_at = VALUES(created_at), expires_at = VALUES(expires_at)";

    public String get(String key) {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement ps = conn.prepareStatement(GET_SQL)) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("data_json");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Không đọc được bộ nhớ đệm thời tiết (mã SQL: " + e.getSQLState() + ").");
        }
        return null;
    }

    public void put(String key, String json, int ttlMinutes) {
        if (ttlMinutes <= 0) throw new IllegalArgumentException("TTL phải lớn hơn 0.");
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
                ps.setString(1, key);
                ps.setString(2, json);
                ps.setString(3, key.startsWith("openweather:geo:") ? "geocoding"
                        : key.startsWith("openweather:weather:") ? "weather" : "generic");
                ps.setInt(4, ttlMinutes);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Không cập nhật được bộ nhớ đệm thời tiết (mã SQL: " + e.getSQLState() + ").");
        }
    }
}
