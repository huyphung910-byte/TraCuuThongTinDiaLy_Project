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
}