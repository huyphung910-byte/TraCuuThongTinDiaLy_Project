package service;

import com.google.gson.*;
import model.Location;
import model.WeatherData;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Read-only city lookup. Does not depend on any DAO or database cache. */
public final class CityAPIService {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final Gson gson = new Gson();
    private final String geocodingUrl;
    private final WeatherAPIService weatherAPIService;
    private final String placesUrl;
    private final String fallbackPlacesUrl;

    public CityAPIService() {
        this("https://geocoding-api.open-meteo.com/v1/search",
                new WeatherAPIService(),
                "https://vi.wikipedia.org/w/api.php", "https://en.wikipedia.org/w/api.php");
    }

    // Endpoint injection for integration tests with a local HTTP server.
    CityAPIService(String geocodingUrl, WeatherAPIService weatherAPIService, String placesUrl) {
        this(geocodingUrl, weatherAPIService, placesUrl, null);
    }

    CityAPIService(String geocodingUrl, WeatherAPIService weatherAPIService, String placesUrl, String fallbackPlacesUrl) {
        this.geocodingUrl = geocodingUrl;
        this.weatherAPIService = weatherAPIService;
        this.placesUrl = placesUrl;
        this.fallbackPlacesUrl = fallbackPlacesUrl;
    }

    public List<Location> search(String keyword) throws IOException {
        try { keyword = util.InputValidation.keyword(keyword, "Tên thành phố", 2, 200); }
        catch (util.InputValidation.ValidationException error) { throw new IOException(error.getMessage()); }
        JsonObject json = fetch(geocodingUrl + "?name="
                + URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8)
                + "&count=20&language=vi&format=json");
        List<Location> cities = new ArrayList<>();
        if (!json.has("results")) return cities;
        try {
            for (JsonElement item : json.getAsJsonArray("results")) {
                JsonObject city = item.getAsJsonObject();
                // GeoNames P* codes identify populated places; exclude postal/other features.
                if (!string(city, "feature_code", "").startsWith("P")) continue;
                Location location = new Location(city.get("id").getAsInt(),
                        city.get("name").getAsString(), string(city, "country", "Chưa có dữ liệu"),
                        city.get("latitude").getAsDouble(), city.get("longitude").getAsDouble(),
                        string(city, "admin1", ""));
                if (city.has("population") && !city.get("population").isJsonNull()) {
                    location.setPopulation(city.get("population").getAsLong());
                }
                cities.add(location);
                location.setTimezone(string(city, "timezone", ""));
                location.setCountryCode(string(city, "country_code", ""));
            }
        } catch (RuntimeException e) {
            throw new IOException("Dữ liệu thành phố từ API không hợp lệ.", e);
        }
        return cities;
    }

    public JsonObject getDetails(Location city) {
        // Parallel calls keep the total API wait below the socket's 10-second response timeout.
        CompletableFuture<JsonObject> weather = CompletableFuture.supplyAsync(() -> weather(city));
        CompletableFuture<JsonObject> places = CompletableFuture.supplyAsync(() -> places(city));
        JsonObject details = weather.join();
        JsonObject nearby = places.join();
        nearby.entrySet().forEach(entry -> details.add(entry.getKey(), entry.getValue()));
        return details;
    }

    private JsonObject weather(Location city) {
        JsonObject result = new JsonObject();
        try {
            WeatherData data = weatherAPIService.getWeatherData(city.getCityName(),
                    city.getLatitude(), city.getLongitude());
            result.add("weather", gson.toJsonTree(data));
        } catch (IOException e) {
            result.addProperty("weatherError", e.getMessage());
        }
        return result;
    }

    private JsonObject places(Location city) {
        JsonObject result = new JsonObject();
        JsonArray nearby = new JsonArray();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(7);
        try {
            String endpoint = placesUrl;
            JsonArray found = searchPlaces(city, endpoint, deadline);
            if (found.isEmpty() && fallbackPlacesUrl != null) {
                endpoint = fallbackPlacesUrl;
                found = searchPlaces(city, endpoint, deadline);
                if (!found.isEmpty()) result.addProperty("placesNotice",
                        "Wikipedia tiếng Việt chưa có địa điểm lân cận; đang hiển thị bài viết Wikipedia tiếng Anh.");
            }
            Map<Integer, JsonObject> byId = new LinkedHashMap<>();
            for (JsonElement item : found) {
                JsonObject place = item.getAsJsonObject();
                JsonObject mapped = new JsonObject();
                mapped.addProperty("name", place.get("title").getAsString());
                mapped.addProperty("distance", place.get("dist").getAsDouble());
                mapped.addProperty("url", URI.create(endpoint).resolve("/?curid=" + place.get("pageid").getAsInt()).toString());
                nearby.add(mapped);
                byId.put(place.get("pageid").getAsInt(), mapped);
            }
            if (!byId.isEmpty()) {
                try {
                    String ids = byId.keySet().stream().map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining("%7C"));
                    JsonObject enriched = fetch(endpoint + "?action=query&pageids=" + ids
                            + "&prop=pageimages%7Cpageprops&ppprop=wikibase-shortdesc"
                            + "&piprop=thumbnail&pithumbsize=240&format=json", remaining(deadline));
                    for (JsonElement item : enriched.getAsJsonObject("query").getAsJsonObject("pages").asMap().values()) {
                        JsonObject page = item.getAsJsonObject();
                        JsonObject mapped = byId.get(page.get("pageid").getAsInt());
                        if (mapped == null) continue;
                        if (page.has("thumbnail")) {
                            mapped.addProperty("imageUrl", string(page.getAsJsonObject("thumbnail"), "source", ""));
                        }
                        if (page.has("pageprops")) {
                            mapped.addProperty("description", string(page.getAsJsonObject("pageprops"), "wikibase-shortdesc", ""));
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    result.addProperty("placesMetadataError", "Không tải được ảnh/mô tả; vẫn có thể xem bài viết Wikipedia. "
                            + apiError(e));
                }
            }
        } catch (IOException | RuntimeException e) {
            result.addProperty("placesError", "Không tải được địa điểm lân cận từ Wikipedia. " + apiError(e));
        }
        result.add("places", nearby);
        return result;
    }

    private JsonArray searchPlaces(Location city, String endpoint, long deadline) throws IOException {
        JsonObject json = fetch(endpoint + "?action=query&list=geosearch&gscoord="
                + city.getLatitude() + "%7C" + city.getLongitude()
                + "&gsradius=10000&gslimit=10&format=json", remaining(deadline));
        return json.getAsJsonObject("query").getAsJsonArray("geosearch");
    }

    private static String apiError(Exception error) {
        return error instanceof IOException && error.getMessage() != null
                ? error.getMessage() : "Dữ liệu API không hợp lệ.";
    }

    private JsonObject fetch(String url) throws IOException {
        return fetch(url, Duration.ofSeconds(6));
    }

    private static Duration remaining(long deadline) throws IOException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new IOException("Hết thời gian chờ Wikipedia.");
        return Duration.ofNanos(nanos);
    }

    private JsonObject fetch(String url, Duration timeout) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "TraCuuThongTinDiaLy/1.0 (Java educational city lookup)")
                .GET().build();
        try {
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IOException("API trả về lỗi HTTP " + response.statusCode() + ".");
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.has("error")) throw new IOException("API trả về lỗi.");
            return json;
        } catch (HttpTimeoutException e) {
            throw new IOException("Hết thời gian chờ API.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Đã hủy yêu cầu API.", e);
        } catch (RuntimeException e) {
            throw new IOException("API trả về dữ liệu không hợp lệ.", e);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("API ")) throw e;
            throw new IOException("Không kết nối được API. Kiểm tra Internet.");
        }
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

}
