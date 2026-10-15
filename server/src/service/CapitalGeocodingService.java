package service;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;

/** Resolves a known capital inside its country, without choosing the first same-name city. */
public final class CapitalGeocodingService {
    private static final long TTL_MILLIS = Duration.ofHours(24).toMillis();
    private static final int MAX_ENTRIES = 256;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String endpoint;
    private final LongSupplier clock;
    private final Map<String, CachedCoordinates> cache = new LinkedHashMap<>(16, 0.75f, true);

    public CapitalGeocodingService() {
        this("https://geocoding-api.open-meteo.com/v1/search", System::currentTimeMillis);
    }

    CapitalGeocodingService(String endpoint) { this(endpoint, System::currentTimeMillis); }
    CapitalGeocodingService(String endpoint, LongSupplier clock) { this.endpoint = endpoint; this.clock = clock; }

    public Coordinates resolve(String capital, String countryCode, long deadline) throws IOException {
        if (capital == null || capital.trim().length() < 2 || capital.length() > 200
                || countryCode == null || !countryCode.trim().matches("[A-Za-z]{2}")) {
            throw new IOException("Thiếu tên thủ đô hoặc mã quốc gia hợp lệ để lấy thời tiết.");
        }
        String fullName = capital.trim();
        String code = countryCode.trim().toUpperCase(Locale.ROOT);
        String cacheKey = code + ":" + normalize(fullName);
        synchronized (cache) {
            CachedCoordinates stored = cache.get(cacheKey);
            if (stored != null && stored.expiresAt > clock.getAsLong()) return stored.coordinates;
            if (stored != null) cache.remove(cacheKey);
        }
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IOException("Hết thời gian chờ tọa độ thủ đô.");
        // Open-Meteo treats a comma as an administrative qualifier; use the city portion and an ISO filter.
        String queryName = fullName.split(",", 2)[0].trim();
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "?name="
                        + URLEncoder.encode(queryName, StandardCharsets.UTF_8) + "&countryCode=" + code
                        + "&count=100&language=en&format=json"))
                .timeout(Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(2).toNanos())))
                .header("User-Agent", "TraCuuThongTinDiaLy/1.0 (capital coordinates)").GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("API tọa độ thủ đô lỗi HTTP " + response.statusCode() + ".");
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.has("error")) throw new IOException("API tọa độ thủ đô trả về lỗi.");
            Coordinates coordinates = choose(json, fullName, code);
            synchronized (cache) {
                cache.put(cacheKey, new CachedCoordinates(coordinates, clock.getAsLong() + TTL_MILLIS));
                while (cache.size() > MAX_ENTRIES) cache.remove(cache.keySet().iterator().next());
            }
            return coordinates;
        } catch (HttpTimeoutException e) {
            throw new IOException("Hết thời gian chờ API tọa độ thủ đô.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Đã hủy yêu cầu tọa độ thủ đô.");
        } catch (IOException e) {
            String message = e.getMessage();
            if (message != null && (message.startsWith("API") || message.startsWith("Không")
                    || message.startsWith("Tọa độ"))) throw e;
            throw new IOException("Không kết nối được API tọa độ thủ đô. Hãy kiểm tra Internet.");
        } catch (RuntimeException e) {
            throw new IOException("API tọa độ thủ đô trả về dữ liệu thiếu hoặc không hợp lệ.");
        }
    }

    private static Coordinates choose(JsonObject json, String fullName, String code) throws IOException {
        if (!json.has("results") || json.getAsJsonArray("results").isEmpty()) {
            throw new IOException("Không tìm thấy tọa độ thủ đô " + fullName + " trong quốc gia " + code + ".");
        }
        Map<String, Coordinates> capitals = new LinkedHashMap<>();
        Map<String, Coordinates> exact = new LinkedHashMap<>();
        boolean invalidCapitalCoordinates = false, invalidExactCoordinates = false;
        for (JsonElement element : json.getAsJsonArray("results")) {
            if (!element.isJsonObject()) continue;
            JsonObject candidate = element.getAsJsonObject();
            String country = string(candidate, "country_code"), feature = string(candidate, "feature_code");
            if (!code.equalsIgnoreCase(country) || !feature.startsWith("P")) continue;
            String name = string(candidate, "name");
            boolean nationalCapital = feature.equals("PPLC");
            boolean exactName = normalize(name).equals(normalize(fullName));
            if (!nationalCapital && !exactName) continue;
            Double lat, lon;
            try {
                lat = number(candidate, "latitude");
                lon = number(candidate, "longitude");
            } catch (RuntimeException e) { lat = null; lon = null; }
            if (lat == null || lon == null || !Double.isFinite(lat) || !Double.isFinite(lon)
                    || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
                if (nationalCapital) invalidCapitalCoordinates = true;
                if (exactName) invalidExactCoordinates = true;
                continue;
            }
            Coordinates coords = new Coordinates(lat, lon);
            String identity = lat + ":" + lon;
            if (nationalCapital) capitals.put(identity, coords);
            if (exactName) exact.put(identity, coords);
        }
        if (capitals.size() == 1 && !invalidCapitalCoordinates) return capitals.values().iterator().next();
        if (capitals.size() > 1 || exact.size() > 1) {
            throw new IOException("Tọa độ thủ đô " + fullName + " có nhiều kết quả trong quốc gia " + code + ".");
        }
        if (invalidCapitalCoordinates || invalidExactCoordinates) throw new IOException("API trả về tọa độ thủ đô thiếu hoặc không hợp lệ.");
        if (exact.size() == 1) return exact.values().iterator().next();
        throw new IOException("Không xác định được đúng thủ đô " + fullName + " trong quốc gia " + code + ".");
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
    }
    private static Double number(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsDouble() : null;
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT).trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("\\s+", " ");
    }
    public static final class Coordinates {
        private final double latitude;
        private final double longitude;
        Coordinates(double latitude, double longitude) { this.latitude = latitude; this.longitude = longitude; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }
    private static final class CachedCoordinates {
        final Coordinates coordinates;
        final long expiresAt;
        CachedCoordinates(Coordinates coordinates, long expiresAt) { this.coordinates = coordinates; this.expiresAt = expiresAt; }
    }
}
