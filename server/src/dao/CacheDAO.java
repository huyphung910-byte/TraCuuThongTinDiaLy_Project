package dao;

import config.DBConnection;

import java.sql.*;

public class CacheDAO {
    private static final String GET_SQL =
            "SELECT du_lieu_json FROM bo_nho_dem_thoi_tiet " +
            "WHERE khoa_bo_nho_dem = ? AND thoi_gian_het_han > NOW()";
    private static final String UPSERT_SQL =
            "INSERT INTO bo_nho_dem_thoi_tiet (khoa_bo_nho_dem, du_lieu_json, thoi_gian_het_han) " +
            "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? MINUTE)) " +
            "ON DUPLICATE KEY UPDATE du_lieu_json = VALUES(du_lieu_json), thoi_gian_het_han = VALUES(thoi_gian_het_han)";

    public String get(String key) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(GET_SQL)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("du_lieu_json");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void put(String key, String json, int ttlMinutes) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, key);
            ps.setString(2, json);
            ps.setInt(3, ttlMinutes);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
