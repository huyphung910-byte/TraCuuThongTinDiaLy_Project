package util;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** UTF-8 configuration, with optional JVM property overrides for IDEs and tests. */
public final class AppConfig {
    private final Properties values = new Properties();

    public static AppConfig load(String component) throws IOException {
        String override = System.getProperty(component + ".config");
        Path path = override != null ? Path.of(override) : Path.of(component, "config.properties");
        if (override == null && !Files.isRegularFile(path)) path = Path.of("config.properties");
        AppConfig config = new AppConfig();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            config.values.load(reader);
        } catch (IOException e) {
            throw new IOException("Không đọc được file cấu hình: " + path.toAbsolutePath(), e);
        }
        return config;
    }

    public String get(String key) throws IOException {
        String value = System.getProperty(key, values.getProperty(key));
        if (value == null) throw new IOException("Thiếu cấu hình: " + key);
        return value;
    }

    public String required(String key) throws IOException {
        String value = get(key).trim();
        if (value.isEmpty()) throw new IOException("Cấu hình không được để trống: " + key);
        return value;
    }

    public int positiveInt(String key, int maximum) throws IOException {
        try {
            int value = Integer.parseInt(required(key));
            if (value > 0 && value <= maximum) return value;
        } catch (NumberFormatException ignored) { }
        throw new IOException("Cấu hình " + key + " phải nằm trong khoảng 1–" + maximum);
    }
}
