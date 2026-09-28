package dao;

import config.DBConnection;
import model.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LocationDAO {
    private static final String SEARCH_BY_NAME_SQL =
            "SELECT id, ten_dia_diem, vi_do, kinh_do, ghi_chu " +
            "FROM dia_diem_da_luu " +
            "WHERE ten_dia_diem LIKE ? " +
            "ORDER BY ten_dia_diem";
    private static final String SAVE_LOCATION_SQL =
            "INSERT INTO dia_diem_da_luu (id_tai_khoan, ten_dia_diem, vi_do, kinh_do, ghi_chu) VALUES (?, ?, ?, ?, ?)";
    private static final String GET_BY_USER_SQL =
            "SELECT id, ten_dia_diem, vi_do, kinh_do, ghi_chu FROM dia_diem_da_luu WHERE id_tai_khoan = ? ORDER BY ngay_luu DESC";
    private static final String DELETE_SQL =
            "DELETE FROM dia_diem_da_luu WHERE id = ? AND id_tai_khoan = ?";

    public List<Location> searchByName(String keyword) {
        List<Location> locations = new ArrayList<>();
        String searchPattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SEARCH_BY_NAME_SQL)) {
            statement.setString(1, searchPattern);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    locations.add(new Location(
                            resultSet.getInt("id"),
                            resultSet.getString("ten_dia_diem"),
                            null,
                            resultSet.getDouble("vi_do"),
                            resultSet.getDouble("kinh_do"),
                            resultSet.getString("ghi_chu")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return locations;
    }

    public boolean saveLocation(int userId, String name, double lat, double lng, String note) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE_LOCATION_SQL)) {
            statement.setInt(1, userId);
            statement.setString(2, name);
            statement.setDouble(3, lat);
            statement.setDouble(4, lng);
            statement.setString(5, note);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Location> getByUserId(int userId) {
        List<Location> locations = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_BY_USER_SQL)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    locations.add(new Location(
                            resultSet.getInt("id"),
                            resultSet.getString("ten_dia_diem"),
                            null, // country is null because table dia_diem_da_luu doesn't have it
                            resultSet.getDouble("vi_do"),
                            resultSet.getDouble("kinh_do"),
                            resultSet.getString("ghi_chu")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return locations;
    }

    public boolean deleteById(int id, int userId) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setInt(1, id);
            statement.setInt(2, userId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}