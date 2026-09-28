package dao;

import config.DBConnection;
import model.Hotel;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HotelDAO {
    private static final String SEARCH_SQL =
            "SELECT id, ten_khach_san, ten_thanh_pho, dia_chi, gia_mot_dem, don_vi_tien, danh_gia, mo_ta " +
            "FROM khach_san WHERE ten_thanh_pho LIKE ? ORDER BY danh_gia DESC";

    public List<Hotel> getHotelsByCity(String cityName) {
        List<Hotel> hotels = new ArrayList<>();
        String pattern = "%" + (cityName == null ? "" : cityName.trim()) + "%";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SEARCH_SQL)) {
            ps.setString(1, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hotels.add(new Hotel(
                            rs.getInt("id"),
                            rs.getString("ten_khach_san"),
                            rs.getString("ten_thanh_pho"),
                            rs.getString("dia_chi"),
                            rs.getDouble("gia_mot_dem"),
                            rs.getString("don_vi_tien"),
                            rs.getDouble("danh_gia"),
                            rs.getString("mo_ta")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return hotels;
    }
}
