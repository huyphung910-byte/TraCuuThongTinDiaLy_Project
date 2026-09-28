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
    private static final String REGISTER_SQL = 
            "INSERT INTO tai_khoan (ten_dang_nhap, mat_khau, ho_ten, email) VALUES (?, ?, ?, ?)";
    private static final String UPDATE_PROFILE_SQL = 
            "UPDATE tai_khoan SET ho_ten = ?, email = ? WHERE id = ?";

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

    public boolean register(String username, String password, String fullname, String email) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(REGISTER_SQL)) {
            statement.setString(1, username);
            statement.setString(2, password);
            statement.setString(3, fullname);
            statement.setString(4, email);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateProfile(int id, String fullname, String email) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_PROFILE_SQL)) {
            statement.setString(1, fullname);
            statement.setString(2, email);
            statement.setInt(3, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}