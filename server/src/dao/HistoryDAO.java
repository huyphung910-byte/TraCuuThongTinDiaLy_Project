package dao;

import config.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HistoryDAO {
    private static final String SAVE_HISTORY_SQL =
            "INSERT INTO lich_su_tra_cuu (id_tai_khoan, tu_khoa_tim_kiem) VALUES (?, ?)";
    private static final String GET_HISTORY_SQL =
            "SELECT tu_khoa_tim_kiem FROM lich_su_tra_cuu " +
            "WHERE id_tai_khoan = ? ORDER BY thoi_gian_tra_cuu DESC";

    public boolean saveHistory(int userId, String queryText) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE_HISTORY_SQL)) {
            statement.setInt(1, userId);
            statement.setString(2, queryText);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<String> getHistoryByUserId(int userId) {
        List<String> history = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_HISTORY_SQL)) {
            statement.setInt(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    history.add(resultSet.getString("tu_khoa_tim_kiem"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return history;
    }
}