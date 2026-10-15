package dao;

import config.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WeatherSubscriptionDAO {
    private static final String SUBSCRIBE_SQL =
            "INSERT INTO theo_doi_thoi_tiet (id_tai_khoan, ten_dia_diem, vi_do, kinh_do, dieu_kien_canh_bao) " +
            "VALUES (?, ?, ?, ?, ?)";
    private static final String GET_BY_USER_SQL =
            "SELECT id, id_tai_khoan, ten_dia_diem, vi_do, kinh_do, dieu_kien_canh_bao " +
            "FROM theo_doi_thoi_tiet WHERE id_tai_khoan = ? ORDER BY ngay_dang_ky DESC";
    private static final String GET_ALL_SQL =
            "SELECT id, id_tai_khoan, ten_dia_diem, vi_do, kinh_do, dieu_kien_canh_bao FROM theo_doi_thoi_tiet";
    private static final String DELETE_SQL =
            "DELETE FROM theo_doi_thoi_tiet WHERE id = ? AND id_tai_khoan = ?";

    public boolean subscribe(int userId, String location, double lat, double lng, String condition) {
        return subscribe(userId, location, Double.valueOf(lat), Double.valueOf(lng), condition);
    }

    /** A subscription created from a city name has no coordinates until geocoding succeeds. */
    public boolean subscribe(int userId, String location, Double lat, Double lng, String condition) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SUBSCRIBE_SQL)) {
            ps.setInt(1, userId);
            ps.setString(2, location);
            if (lat == null) ps.setNull(3, Types.DOUBLE);
            else ps.setDouble(3, lat);
            if (lng == null) ps.setNull(4, Types.DOUBLE);
            else ps.setDouble(4, lng);
            ps.setString(5, condition);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Không lưu được đăng ký theo dõi thời tiết (mã SQL: " + e.getSQLState() + ").");
            return false;
        }
    }

    public List<String[]> getByUserId(int userId) {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(GET_BY_USER_SQL)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            String.valueOf(rs.getInt("id_tai_khoan")),
                            rs.getString("ten_dia_diem"),
                            rs.getString("vi_do"),
                            rs.getString("kinh_do"),
                            rs.getString("dieu_kien_canh_bao")
                    });
                }
            }
        } catch (SQLException e) {
            System.err.println("Không đọc được đăng ký theo dõi thời tiết của tài khoản (mã SQL: " + e.getSQLState() + ").");
        }
        return list;
    }

    public List<String[]> getAll() {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(GET_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        String.valueOf(rs.getInt("id_tai_khoan")),
                        rs.getString("ten_dia_diem"),
                        rs.getString("vi_do"),
                        rs.getString("kinh_do"),
                        rs.getString("dieu_kien_canh_bao")
                });
            }
        } catch (SQLException e) {
            System.err.println("Không đọc được danh sách theo dõi thời tiết (mã SQL: "
                    + e.getSQLState() + "): " + e.getMessage());
        }
        return list;
    }

    public boolean delete(int id, int userId) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Không xóa được đăng ký theo dõi thời tiết (mã SQL: " + e.getSQLState() + ").");
            return false;
        }
    }
}
