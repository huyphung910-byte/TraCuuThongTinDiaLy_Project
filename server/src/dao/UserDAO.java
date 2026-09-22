package dao;

import config.DBConnection;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {
    private static final String CHECK_LOGIN_SQL =
            "SELECT id, ten_dang_nhap, mat_khau, ho_ten, email " +
            "FROM tai_khoan WHERE ten_dang_nhap = ? AND mat_khau = ?";

    public User checkLogin(String username, String password) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(CHECK_LOGIN_SQL)) {
            statement.setString(1, username);
            statement.setString(2, password);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new User(
                            resultSet.getInt("id"),
                            resultSet.getString("ten_dang_nhap"),
                            resultSet.getString("mat_khau"),
                            resultSet.getString("ho_ten"),
                            resultSet.getString("email")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }
}