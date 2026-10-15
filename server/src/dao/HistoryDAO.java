package dao;

import config.DBConnection;
import model.HistoryEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HistoryDAO {
    private static final String SAVE_HISTORY_SQL =
            "INSERT INTO lich_su_tra_cuu (id_tai_khoan, tu_khoa_tim_kiem, loai_tra_cuu) VALUES (?, ?, ?)";
    private static final String GET_HISTORY_SQL =
            "SELECT tu_khoa_tim_kiem, loai_tra_cuu FROM lich_su_tra_cuu " +
            "WHERE id_tai_khoan = ? ORDER BY thoi_gian_tra_cuu DESC, id DESC";

    public boolean saveHistory(int userId, String queryText, String type) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE_HISTORY_SQL)) {
            statement.setInt(1, userId);
            statement.setString(2, queryText);
            statement.setString(3, type);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Không lưu được lịch sử tra cứu (mã SQL: " + e.getSQLState() + ").");
            return false;
        }
    }

    public List<HistoryEntry> getHistoryByUserId(int userId) {
        List<HistoryEntry> history = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_HISTORY_SQL)) {
            statement.setInt(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    history.add(new HistoryEntry(resultSet.getString("tu_khoa_tim_kiem"),
                            resultSet.getString("loai_tra_cuu")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Không đọc được lịch sử tra cứu (mã SQL: " + e.getSQLState() + ").");
        }

        return history;
    }
}
