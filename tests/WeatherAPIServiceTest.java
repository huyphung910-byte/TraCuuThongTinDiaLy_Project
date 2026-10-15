package service;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import model.Message;
import model.WeatherData;
import java.io.IOException;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.concurrent.atomic.*;

public final class WeatherAPIServiceTest {
    private static final String TEST_KEY = "private-fixture-key";

    public static void main(String[] args) throws Exception {
        HttpServer fixture = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        AtomicInteger geoCalls = new AtomicInteger();
        AtomicInteger weatherCalls = new AtomicInteger();
        AtomicInteger geoStatus = new AtomicInteger(200);
        AtomicInteger weatherStatus = new AtomicInteger(200);
        AtomicReference<String> geoBody = new AtomicReference<>(
                "[{\"name\":\"Hanoi\",\"lat\":21.03,\"lon\":105.85,\"country\":\"VN\"}]");
        AtomicReference<String> weatherBody = new AtomicReference<>("""
                {"main":{"temp":29.5,"humidity":80},"wind":{"speed":3.2},
                 "weather":[{"description":"mưa lớn"}],"dt":1791194400}
                """);
        AtomicReference<String> weatherQuery = new AtomicReference<>();
        fixture.createContext("/geo", exchange -> {
            geoCalls.incrementAndGet();
            byte[] bytes = geoBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(geoStatus.get(), bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        fixture.createContext("/weather", exchange -> {
            weatherCalls.incrementAndGet();
            weatherQuery.set(exchange.getRequestURI().getQuery());
            byte[] bytes = weatherBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(weatherStatus.get(), bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        fixture.start();
        try {
            String base = "http://localhost:" + fixture.getAddress().getPort();
            WeatherAPIService api = new WeatherAPIService(base + "/geo", base + "/weather", () -> TEST_KEY);
            WeatherAPIService missing = new WeatherAPIService(base + "/geo", base + "/weather", () -> " ");
            expectError(() -> missing.getWeatherData("Hanoi"), "Thiếu khóa API");
            check(geoCalls.get() == 0 && weatherCalls.get() == 0, "Missing key must not send HTTP requests");
            WeatherData result = api.getWeatherData("Hanoi,VN");
            check(result.getTemp() == 29.5 && result.getHumidity() == 80 && result.getWindSpeed() == 3.2,
                    "Weather fields must come from API");
            check(result.getDescription().equals("mưa lớn"), "Preserve Vietnamese API description");
            check(geoCalls.get() == 1 && weatherCalls.get() == 1, "Geocoding before weather");
            check(weatherQuery.get().contains("lat=21.03") && weatherQuery.get().contains("lon=105.85")
                    && !weatherQuery.get().contains("q="), "Weather endpoint must use coordinates, not city name");
            check(weatherQuery.get().contains("lang=vi"), "Weather API must request Vietnamese descriptions");
            api.getWeatherData("Selected city", 10.8, 106.6);
            check(geoCalls.get() == 1, "Do not re-resolve an already selected city");
            check(weatherQuery.get().contains("lat=10.8") && weatherQuery.get().contains("lon=106.6"), "Selected coordinates");
            expectError(() -> api.getWeatherData("Invalid", 100, 0), "Tọa độ");
            int before = weatherCalls.get();
            geoBody.set("[]");
            expectError(() -> api.getWeatherData("Unknown"), "Không tìm thấy");
            geoBody.set("[{},{ }]");
            expectError(() -> api.getWeatherData("Paris"), "nhiều kết quả");
            geoBody.set("not-json");
            expectError(() -> api.getWeatherData("Hanoi"), "không hợp lệ");
            check(weatherCalls.get() == before, "Failed geocoding must not request weather");
            for (int status : new int[]{401, 403, 429, 500}) {
                geoStatus.set(status);
                expectError(() -> api.getWeatherData("Hanoi"), "HTTP " + status);
                weatherStatus.set(status);
                expectError(() -> api.getWeatherData("Hanoi", 21, 105), "HTTP " + status);
            }
            geoStatus.set(200);
            weatherStatus.set(200);
            weatherBody.set("{}");
            expectError(() -> api.getWeatherData("Hanoi", 21, 105), "thiếu hoặc không hợp lệ");

            weatherBody.set("{\"main\":{\"temp\":31,\"humidity\":70},\"wind\":{\"speed\":2},"
                    + "\"weather\":[{\"description\":\"trời quang\"}],\"dt\":1791194400}");
            MemoryCache cache = new MemoryCache();
            cache.put("openweather:weather:v1:metric:10.0:106.0",
                    weatherBody.get().replace("trời quang", "clear sky"), 5);
            WeatherAPIService cachedApi = new WeatherAPIService(base + "/geo", base + "/weather", () -> TEST_KEY, cache, 5);
            int calls = weatherCalls.get();
            WeatherData fresh = cachedApi.getWeatherData("A", 10, 106);
            check(!fresh.isFromCache() && fresh.getDescription().equals("trời quang"),
                    "Old English cache must not override Vietnamese weather requests");
            WeatherAPIService otherClient = new WeatherAPIService(base + "/geo", base + "/weather", () -> "", cache, 5);
            WeatherData hit = otherClient.getWeatherData("B", 10, 106);
            check(hit.isFromCache() && hit.getCityName().equals("B") && weatherCalls.get() == calls + 1,
                    "Shared cache hit must skip API and preserve requested city name");
            cache.now += 300_000;
            weatherBody.set(weatherBody.get().replace("\"temp\":31", "\"temp\":32"));
            check(cachedApi.getWeatherData("A", 10, 106).getTemp() == 32 && weatherCalls.get() == calls + 2,
                    "Expired cache must fetch and update");
            cache.now += 300_000;
            weatherStatus.set(401);
            expectError(() -> cachedApi.getWeatherData("A", 10, 106), "HTTP 401");
            check(cache.get("openweather:weather:v2:metric:vi:10.0:106.0") == null, "Do not serve stale cache or store errors");
            weatherStatus.set(200);
            cachedApi.getWeatherData("A", 10, 106);
            geoBody.set("[{\"name\":\"Hanoi\",\"lat\":21.03,\"lon\":105.85}]");
            int geocalls = geoCalls.get();
            cachedApi.getWeatherData("Hanoi,VN");
            calls = weatherCalls.get();
            check(cachedApi.getWeatherData("hanoi,vn").isFromCache() && geoCalls.get() == geocalls + 1
                    && weatherCalls.get() == calls, "Name lookup must reuse geocoding and weather caches");

            // Verify GET_WEATHER exposes a clear error to the client instead of a success/mock response.
            Class<?> handlerClass = Class.forName("ClientHandler");
            Object handler = handlerClass.getConstructor(Socket.class, KeyPair.class).newInstance(new Socket(), null);
            Field identity = handlerClass.getDeclaredField("currentUserId");
            identity.setAccessible(true);
            identity.set(handler, 1);
            Field apiField = handlerClass.getDeclaredField("weatherAPIService");
            apiField.setAccessible(true);
            apiField.set(handler, missing);
            Method request = handlerClass.getDeclaredMethod("handleRequest", String.class);
            request.setAccessible(true);
            JsonObject data = new JsonObject();
            data.addProperty("cityName", "Hanoi");
            JsonObject response = JsonParser.parseString((String) request.invoke(handler,
                    new Message("GET_WEATHER", data).toJson())).getAsJsonObject();
            check(response.get("action").getAsString().equals("ERROR")
                    && response.get("data").getAsString().contains("Thiếu khóa API"), "Error response routing");
            monitorLocalizationTests();
            System.out.println("ĐẠT: định vị -> tọa độ -> thời tiết, khóa thiếu/không hợp lệ, lỗi HTTP, dữ liệu thiếu/sai, tên trùng và không trả dữ liệu giả");
        } finally { fixture.stop(0); }
    }

    private interface Request { void run() throws IOException; }

    private static void monitorLocalizationTests() throws Exception {
        WeatherMonitorService monitor = new WeatherMonitorService();
        Method shouldAlert = WeatherMonitorService.class.getDeclaredMethod("shouldAlert", WeatherData.class, String.class);
        shouldAlert.setAccessible(true);
        try {
            WeatherData rain = new WeatherData("Fixture", 25, 80, "mưa lớn", 2, "2026-10-06T00:00:00Z");
            check((Boolean) shouldAlert.invoke(monitor, rain, "Mưa lớn & Bão"), "Vietnamese rain alerts must continue to work");
            rain.setDescription("dông kèm sấm");
            check((Boolean) shouldAlert.invoke(monitor, rain, "Mưa lớn & Bão"), "Vietnamese thunderstorm alerts");
            rain.setDescription("tuyết rơi");
            check((Boolean) shouldAlert.invoke(monitor, rain, "Tuyết rơi nặng hạt"), "Vietnamese snow alerts");
            rain.setDescription("heavy rain");
            check((Boolean) shouldAlert.invoke(monitor, rain, "Mưa lớn & Bão"), "Existing English alerts remain supported");
            rain.setDescription("trời quang");
            check(!(Boolean) shouldAlert.invoke(monitor, rain, "Mưa lớn & Bão"), "Clear weather must not trigger rain warning");
        } finally { monitor.stop(); }
    }

    private static final class MemoryCache extends dao.CacheDAO {
        long now;
        private record Entry(String json, long expires) { }
        private final java.util.Map<String, Entry> entries = new java.util.HashMap<>();
        @Override public String get(String key) {
            Entry entry = entries.get(key);
            return entry != null && entry.expires > now ? entry.json : null;
        }
        @Override public void put(String key, String json, int ttl) {
            entries.put(key, new Entry(json, now + ttl * 60_000L));
        }
    }

    private static void expectError(Request request, String text) throws IOException {
        try { request.run(); throw new AssertionError("Expected error: " + text); }
        catch (IOException expected) {
            check(expected.getMessage().contains(text), "Unclear error: " + expected.getMessage());
            check(!expected.getMessage().contains(TEST_KEY) && expected.getCause() == null, "API key leakage");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
