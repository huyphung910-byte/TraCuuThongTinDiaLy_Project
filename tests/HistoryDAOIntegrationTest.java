import dao.HistoryDAO;

/** Use only the disposable database created by DatabaseSchemaTest. */
public final class HistoryDAOIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("db.host", "127.0.0.1");
        System.setProperty("db.port", args[0]);
        System.setProperty("db.name", "geo_schema_v14_test");
        System.setProperty("db.user", "root");
        System.setProperty("db.password", "");
        HistoryDAO dao = new HistoryDAO();
        String keyword = "Việt Nam test " + System.nanoTime();
        if (!dao.saveHistory(1, keyword, "quoc_gia") || !dao.saveHistory(1, keyword, "thanh_pho"))
            throw new AssertionError("Cannot save history");
        var entries = dao.getHistoryByUserId(1).stream()
                .filter(entry -> entry.getKeyword().equals(keyword)).toList();
        if (entries.size() != 2 || !entries.get(0).getType().equals("thanh_pho")
                || !entries.get(1).getType().equals("quoc_gia")) {
            throw new AssertionError("Separate types or ordering were lost");
        }
        if (dao.getHistoryByUserId(2).stream().anyMatch(entry -> entry.getKeyword().equals(keyword)))
            throw new AssertionError("History leaked across users");
        System.out.println("HistoryDAOIntegrationTest: Unicode keyword, separate types, ordering and ownership passed.");
    }
}
