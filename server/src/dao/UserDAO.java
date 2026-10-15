package dao;

import config.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import model.User;
import util.PasswordUtil;

public class UserDAO {

    private static final String FIND_BY_USERNAME_SQL =
            "SELECT id, ten_dang_nhap, mat_khau, ho_ten, email "
                    + "FROM tai_khoan WHERE ten_dang_nhap = ?";

    private static final String REGISTER_SQL =
            "INSERT INTO tai_khoan "
                    + "(ten_dang_nhap, mat_khau, ho_ten, email) "
                    + "VALUES (?, ?, ?, ?)";

    private static final String UPDATE_PROFILE_SQL =
            "UPDATE tai_khoan "
                    + "SET ho_ten = ?, email = ? WHERE id = ?";
    private static final String FIND_BY_ID_SQL =
            "SELECT id, ten_dang_nhap, ho_ten, email FROM tai_khoan WHERE id = ?";
    private static final String FIND_PASSWORD_SQL =
            "SELECT mat_khau FROM tai_khoan WHERE id = ?";
    private static final String CHANGE_PASSWORD_SQL =
            "UPDATE tai_khoan SET mat_khau = ? WHERE id = ? AND BINARY mat_khau = ?";

    public enum PasswordChangeResult {
        SUCCESS, WRONG_CURRENT_PASSWORD, USER_NOT_FOUND, CONCURRENT_CHANGE, DATABASE_ERROR
    }

    /** Returns public profile fields only; never read or serialize the password hash here. */
    public User findById(int id) throws SQLException {
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID_SQL)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new User(result.getInt("id"), result.getString("ten_dang_nhap"),
                        result.getString("ho_ten"), result.getString("email")) : null;
            }
        }
    }

    public PasswordChangeResult changePassword(int id, String currentPassword, String newPassword) {
        try (Connection connection = requireConnection()) {
            String storedHash;
            try (PreparedStatement read = connection.prepareStatement(FIND_PASSWORD_SQL)) {
                read.setInt(1, id);
                try (ResultSet result = read.executeQuery()) {
                    if (!result.next()) return PasswordChangeResult.USER_NOT_FOUND;
                    storedHash = result.getString("mat_khau");
                }
            }
            if (!PasswordUtil.verifyPassword(currentPassword, storedHash)) {
                return PasswordChangeResult.WRONG_CURRENT_PASSWORD;
            }
            String newHash = PasswordUtil.hashPassword(newPassword);
            // Compare the old hash exactly: another concurrent password change must not be overwritten.
            try (PreparedStatement update = connection.prepareStatement(CHANGE_PASSWORD_SQL)) {
                update.setString(1, newHash);
                update.setInt(2, id);
                update.setString(3, storedHash);
                return update.executeUpdate() == 1 ? PasswordChangeResult.SUCCESS : PasswordChangeResult.CONCURRENT_CHANGE;
            }
        } catch (SQLException e) {
            System.err.println("Không đổi được mật khẩu tài khoản (mã SQL: " + e.getSQLState() + ").");
            return PasswordChangeResult.DATABASE_ERROR;
        }
    }

    private static Connection requireConnection() throws SQLException {
        Connection connection = DBConnection.getConnection();
        if (connection == null) throw new SQLException("Không kết nối được cơ sở dữ liệu.");
        return connection;
    }

    public User checkLogin(
            String username,
            String password
    ) {
        try (
                Connection connection =
                        DBConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                FIND_BY_USERNAME_SQL
                        )
        ) {
            statement.setString(1, username);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                if (
                        resultSet.next()
                                && PasswordUtil.verifyPassword(
                                        password,
                                        resultSet.getString(
                                                "mat_khau"
                                        )
                                )
                ) {
                    return new User(
                            resultSet.getInt("id"),
                            resultSet.getString(
                                    "ten_dang_nhap"
                            ),
                            resultSet.getString("ho_ten"),
                            resultSet.getString("email")
                    );
                }
            }

        } catch (SQLException e) {
            System.err.println("Không kiểm tra được tài khoản đăng nhập (mã SQL: " + e.getSQLState() + ").");
        }

        return null;
    }

    public boolean register(
            String username,
            String password,
            String fullname,
            String email
    ) {
        if (password == null || password.isEmpty()) {
            return false;
        }

        String passwordHash =
                PasswordUtil.hashPassword(password);

        try (
                Connection connection =
                        DBConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                REGISTER_SQL
                        )
        ) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, fullname);
            statement.setString(4, email);

            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Không đăng ký được tài khoản (mã SQL: " + e.getSQLState() + ").");
            return false;
        }
    }

    public boolean updateProfile(
            int id,
            String fullname,
            String email
    ) {
        try (
                Connection connection =
                        requireConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                UPDATE_PROFILE_SQL
                        )
        ) {
            statement.setString(1, fullname);
            statement.setString(2, email);
            statement.setInt(3, id);

            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Không cập nhật được thông tin tài khoản (mã SQL: " + e.getSQLState() + ").");
            return false;
        }
    }
}
