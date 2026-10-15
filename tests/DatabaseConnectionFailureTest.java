import config.DBConnection;
import dao.WeatherSubscriptionDAO;
import service.WeatherMonitorService;
import java.net.ServerSocket;
import java.sql.SQLException;

/** A failed connection must never become a null Connection or stop scheduled checks. */
public final class DatabaseConnectionFailureTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("server.config", "build/missing-db-config.properties");
        expectSqlFailure();
        System.clearProperty("server.config");
        int unavailablePort;
        try (ServerSocket reserved = new ServerSocket(0)) { unavailablePort = reserved.getLocalPort(); }
        System.setProperty("db.host", "127.0.0.1");
        System.setProperty("db.port", Integer.toString(unavailablePort));
        expectSqlFailure();
        if (!new WeatherSubscriptionDAO().getAll().isEmpty()) throw new AssertionError();
        WeatherMonitorService monitor = new WeatherMonitorService();
        try {
            var check = WeatherMonitorService.class.getDeclaredMethod("checkWeatherAndAlert");
            check.setAccessible(true);
            check.invoke(monitor);
            check.invoke(monitor);
        } finally { monitor.stop(); }
        System.out.println("DatabaseConnectionFailureTest: missing config and unavailable MySQL handled without NullPointerException.");
    }

    private static void expectSqlFailure() throws Exception {
        try (var connection = DBConnection.getConnection()) {
            throw new AssertionError("Expected SQLException, got connection " + connection);
        } catch (SQLException expected) {
            if (expected.getMessage() == null || expected.getMessage().isBlank()) throw new AssertionError();
        }
    }
}
