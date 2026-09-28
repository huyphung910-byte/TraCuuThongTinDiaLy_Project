package dao;

import config.DBConnection;
import model.Country;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CountryDAO {
    private static final String SEARCH_SQL =
            "SELECT id, ten_quoc_gia, ma_quoc_gia, thu_do, don_vi_tien_te, ngon_ngu, url_quoc_ky, quoc_gia_lien_ke, diem_du_lich_noi_bat " +
            "FROM quoc_gia WHERE ten_quoc_gia LIKE ? OR ma_quoc_gia LIKE ? ORDER BY ten_quoc_gia";

    public List<Country> searchByName(String keyword) {
        List<Country> list = new ArrayList<>();
        String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SEARCH_SQL)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Country(
                            rs.getInt("id"),
                            rs.getString("ten_quoc_gia"),
                            rs.getString("ma_quoc_gia"),
                            rs.getString("thu_do"),
                            rs.getString("don_vi_tien_te"),
                            rs.getString("ngon_ngu"),
                            rs.getString("url_quoc_ky"),
                            rs.getString("quoc_gia_lien_ke"),
                            rs.getString("diem_du_lich_noi_bat")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
