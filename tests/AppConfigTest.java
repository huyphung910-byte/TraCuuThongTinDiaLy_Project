import java.io.IOException;
import java.nio.file.Files;
import util.AppConfig;

public final class AppConfigTest {
    public static void main(String[] args) throws Exception {
        var file = Files.createTempFile("geo-config-", ".properties");
        String previous = System.getProperty("client.config");
        String previousPort = System.getProperty("server.port");
        try {
            System.setProperty("client.config", file.toString());
            System.clearProperty("server.port");
            Files.writeString(file, "server.host=192.168.1.10\nserver.port=9000\ndb.password=\nlabel=Địa lý\n");
            AppConfig config = AppConfig.load("client");
            check(config.required("server.host").equals("192.168.1.10"));
            check(config.positiveInt("server.port", 65535) == 9000);
            check(config.get("db.password").isEmpty());
            check(config.get("label").equals("Địa lý"));
            System.setProperty("server.port", "9100");
            check(config.positiveInt("server.port", 65535) == 9100);
            for (String bad : new String[]{"0", "65536", "abc", ""}) {
                System.setProperty("server.port", bad);
                try { config.positiveInt("server.port", 65535); throw new AssertionError(); }
                catch (IOException expected) { }
            }
            System.setProperty("client.config", file + ".missing");
            try { AppConfig.load("client"); throw new AssertionError(); }
            catch (IOException expected) { }
        } finally {
            if (previous == null) System.clearProperty("client.config");
            else System.setProperty("client.config", previous);
            if (previousPort == null) System.clearProperty("server.port");
            else System.setProperty("server.port", previousPort);
            Files.deleteIfExists(file);
        }
        System.out.println("AppConfigTest: LAN settings, UTF-8, overrides and invalid configuration passed.");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
