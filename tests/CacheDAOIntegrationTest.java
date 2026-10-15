import config.DBConnection;
import dao.CacheDAO;

/** Requires the disposable schema created by DatabaseSchemaTest. */
public final class CacheDAOIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("db.host", "127.0.0.1");
        System.setProperty("db.port", args[0]);
        System.setProperty("db.name", "geo_schema_v14_test");
        System.setProperty("db.user", "root");
        System.setProperty("db.password", "");
        CacheDAO cache = new CacheDAO();
        String key = "openweather:weather:test";
        cache.put(key, "{\"temp\":30}", 5);
        check(cache.get(key).equals("{\"temp\":30}"));
        cache.put(key, "{\"temp\":31}", 10);
        check(cache.get(key).equals("{\"temp\":31}"));
        cache.put("openweather:geo:test", "{}", 5);
        try (var connection = DBConnection.getConnection(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT type, COUNT(*) FROM bo_nho_dem_thoi_tiet WHERE cache_key='" + key + "'")) {
                check(rows.next() && rows.getString(1).equals("weather") && rows.getInt(2) == 1);
            }
            try (var rows = statement.executeQuery("SELECT type FROM bo_nho_dem_thoi_tiet WHERE cache_key='openweather:geo:test'")) {
                check(rows.next() && rows.getString(1).equals("geocoding"));
            }
            statement.executeUpdate("UPDATE bo_nho_dem_thoi_tiet SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE cache_key='" + key + "'");
        }
        check(cache.get(key) == null);
        System.out.println("CacheDAOIntegrationTest: typed cache, refresh, uniqueness and expiry passed.");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
