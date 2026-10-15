package service;

import com.google.gson.*;
import model.WeatherData;
import dao.CacheDAO;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class WeatherAPIService {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final String geocodingUrl;
    private final String weatherUrl;
    private final Supplier<String> keySupplier;
    private final CacheDAO cache;
    private final int ttlMinutes;
    private static final Object[] CACHE_LOCKS = new Object[32];
    static { for (int i = 0; i < CACHE_LOCKS.length; i++) CACHE_LOCKS[i] = new Object(); }

    public WeatherAPIService() {
        this("https://api.openweathermap.org/geo/1.0/direct",
                "https://api.openweathermap.org/data/2.5/weather", () -> {
                    String key = System.getenv("OPENWEATHER_API_KEY");
                    return key == null || key.trim().isEmpty()
                            ? System.getProperty("openweather.api.key", "") : key;
                }, new CacheDAO(), Integer.getInteger("weather.cache.ttlMinutes", 5));
    }

    WeatherAPIService(String geocodingUrl, String weatherUrl, Supplier<String> keySupplier) {
        this(geocodingUrl, weatherUrl, keySupplier, new CacheDAO() {
            @Override public String get(String key) { return null; }
            @Override public void put(String key, String json, int ttl) { }
        }, 5);
    }

    WeatherAPIService(String geocodingUrl, String weatherUrl, Supplier<String> keySupplier, CacheDAO cache, int ttlMinutes) {
        this.geocodingUrl = geocodingUrl;
        this.weatherUrl = weatherUrl;
        this.keySupplier = keySupplier;
        this.cache = cache;
        this.ttlMinutes = Math.max(5, Math.min(10, ttlMinutes));
    }

    public WeatherData getWeatherData(String cityName) throws IOException {
        if (cityName == null || cityName.trim().isEmpty()) {
            throw new IOException("Tên thành phố không được để trống.");
        }
        String geoCacheKey = "openweather:geo:v1:" + cityName.trim().toLowerCase(java.util.Locale.ROOT);
        String cachedGeo = cache.get(geoCacheKey);
        if (cachedGeo != null) {
            try {
                JsonObject place = JsonParser.parseString(cachedGeo).getAsJsonObject();
                return weatherAt(place.get("name").getAsString(), place.get("lat").getAsDouble(),
                        place.get("lon").getAsDouble(), null,
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(7));
            } catch (RuntimeException ignored) { /* Invalid cache: resolve again. */ }
        }
        String key = requireKey();
        // One total deadline for both HTTP calls, below the client's 10-second timeout.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(7);
        JsonElement json = fetch(geocodingUrl + "?q=" + encode(cityName.trim())
                + "&limit=5&appid=" + encode(key), "tra cứu tọa độ", deadline);
        try {
            JsonArray candidates = json.getAsJsonArray();
            if (candidates.isEmpty()) throw new IOException("Không tìm thấy tọa độ thành phố: " + cityName);
            if (candidates.size() > 1) {
                throw new IOException("Tên thành phố có nhiều kết quả. Hãy thêm mã quốc gia/tỉnh, "
                        + "ví dụ Paris,FR, hoặc chọn thành phố từ bảng tra cứu.");
            }
            JsonObject place = candidates.get(0).getAsJsonObject();
            validateCoordinates(place.get("lat").getAsDouble(), place.get("lon").getAsDouble());
            cache.put(geoCacheKey, place.toString(), ttlMinutes);
            return weatherAt(place.get("name").getAsString(), place.get("lat").getAsDouble(),
                    place.get("lon").getAsDouble(), key, deadline);
        } catch (RuntimeException e) {
            throw new IOException("API tra cứu tọa độ OpenWeather trả về dữ liệu không hợp lệ.");
        }
    }

    /** For a city already resolved by a geocoding API, preserve the selected coordinates. */
    public WeatherData getWeatherData(String cityName, double latitude, double longitude) throws IOException {
        return weatherAt(cityName, latitude, longitude, null,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(7));
    }

    private WeatherData weatherAt(String cityName, double lat, double lon, String key, long deadline)
            throws IOException {
        validateCoordinates(lat, lon);
        String cacheKey = "openweather:weather:v2:metric:vi:" + Double.toString(lat == 0 ? 0 : lat)
                + ":" + Double.toString(lon == 0 ? 0 : lon);
        Object lock = CACHE_LOCKS[(cacheKey.hashCode() & Integer.MAX_VALUE) % CACHE_LOCKS.length];
        synchronized (lock) {
            String cached = cache.get(cacheKey);
            if (cached != null) {
                try {
                    WeatherData result = parseWeather(JsonParser.parseString(cached), cityName);
                    result.setFromCache(true);
                    return result;
                } catch (IOException | RuntimeException ignored) { /* Invalid cache: fetch fresh data. */ }
            }
            if (key == null) key = requireKey();
            JsonElement json = fetch(weatherUrl + "?lat=" + lat + "&lon=" + lon
                    + "&appid=" + encode(key) + "&units=metric&lang=vi", "thời tiết", deadline);
            WeatherData result = parseWeather(json, cityName);
            cache.put(cacheKey, json.toString(), ttlMinutes);
            return result;
        }
    }

    private static void validateCoordinates(double lat, double lon) throws IOException {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            throw new IOException("Tọa độ thời tiết không hợp lệ.");
        }
    }

    private WeatherData parseWeather(JsonElement json, String cityName) throws IOException {
        try {
            JsonObject data = json.getAsJsonObject();
            JsonObject main = data.getAsJsonObject("main");
            JsonObject wind = data.getAsJsonObject("wind");
            JsonArray weather = data.getAsJsonArray("weather");
            return new WeatherData(cityName, main.get("temp").getAsDouble(),
                    main.get("humidity").getAsInt(), weather.get(0).getAsJsonObject().get("description").getAsString(),
                    wind.get("speed").getAsDouble(), Instant.ofEpochSecond(data.get("dt").getAsLong()).toString());
        } catch (RuntimeException e) {
            throw new IOException("OpenWeather trả về dữ liệu thời tiết thiếu hoặc không hợp lệ.");
        }
    }

    private String requireKey() throws IOException {
        String key = keySupplier.get();
        if (key == null || key.trim().isEmpty()) {
            throw new IOException("Thiếu khóa API OpenWeather. Cấu hình OPENWEATHER_API_KEY "
                    + "hoặc -Dopenweather.api.key trên máy chủ.");
        }
        return key.trim();
    }

    private JsonElement fetch(String url, String stage, long deadline) throws IOException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IOException("OpenWeather: hết thời gian chờ " + stage + ".");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofNanos(remaining)).GET().build();
        try {
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 401) throw new IOException("OpenWeather: khóa API không hợp lệ hoặc chưa được kích hoạt (HTTP 401).");
            if (status == 403) throw new IOException("OpenWeather: khóa API không có quyền truy cập (HTTP 403).");
            if (status == 429) throw new IOException("OpenWeather: vượt giới hạn yêu cầu API (HTTP 429). Hãy thử lại sau.");
            if (status < 200 || status >= 300) throw new IOException("OpenWeather: lỗi " + stage + " (HTTP " + status + ").");
            return JsonParser.parseString(response.body());
        } catch (HttpTimeoutException e) {
            throw new IOException("OpenWeather: hết thời gian chờ " + stage + ".");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Đã hủy yêu cầu OpenWeather.");
        } catch (IOException e) {
            // Never propagate request URLs or exception causes containing the API key.
            if (e.getMessage() != null && e.getMessage().startsWith("OpenWeather:")) throw e;
            throw new IOException("OpenWeather: không kết nối được API " + stage + ". Kiểm tra Internet.");
        } catch (RuntimeException e) {
            throw new IOException("OpenWeather: phản hồi " + stage + " không hợp lệ.");
        }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
