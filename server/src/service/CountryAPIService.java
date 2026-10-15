package service;

import com.google.gson.*;
import model.Country;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Country metadata comes from the API, never from demo database records. */
public class CountryAPIService {
    private static final long CACHE_MILLIS = Duration.ofHours(24).toMillis();
    private static final long INCOMPLETE_BORDER_CACHE_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final Duration BORDER_TIMEOUT = Duration.ofSeconds(1);
    private static final Map<String, String> NAME_CODES = Map.ofEntries(
            Map.entry("viet nam", "VN"), Map.entry("nhat ban", "JP"),
            Map.entry("han quoc", "KR"), Map.entry("trung quoc", "CN"),
            Map.entry("thai lan", "TH"), Map.entry("phap", "FR"),
            Map.entry("duc", "DE"), Map.entry("anh", "GB"),
            Map.entry("my", "US"), Map.entry("hoa ky", "US"),
            Map.entry("uc", "AU"), Map.entry("nga", "RU"),
            Map.entry("an do", "IN"), Map.entry("y", "IT"),
            Map.entry("tay ban nha", "ES"), Map.entry("united states", "US"),
            Map.entry("united states of america", "US"), Map.entry("south korea", "KR"),
            Map.entry("north korea", "KP"), Map.entry("uk", "GB"),
            Map.entry("campuchia", "KH"), Map.entry("cam pu chia", "KH"),
            Map.entry("lao", "LA"), Map.entry("laos", "LA"), Map.entry("singapore", "SG"),
            Map.entry("xin ga po", "SG"), Map.entry("malaysia", "MY"),
            Map.entry("ma lai xi a", "MY"), Map.entry("indonesia", "ID"),
            Map.entry("in do ne xi a", "ID"), Map.entry("philippines", "PH"),
            Map.entry("phi lip pin", "PH"), Map.entry("dai loan", "TW"),
            Map.entry("canada", "CA"), Map.entry("ca na da", "CA"),
            Map.entry("brazil", "BR"), Map.entry("bra xin", "BR"),
            Map.entry("nam phi", "ZA"), Map.entry("thuy si", "CH"));
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final String apiBase;
    private final LongSupplier now;
    private final Map<String, CachedCountry> cache = new ConcurrentHashMap<>();

    public CountryAPIService() { this("https://countries.dev"); }

    // Use a local HTTP fixture for deterministic integration tests.
    CountryAPIService(String apiBase) { this(apiBase, System::currentTimeMillis); }

    CountryAPIService(String apiBase, LongSupplier now) {
        this.apiBase = apiBase.replaceAll("/+$", "");
        this.now = now;
    }

    /** Kept for callers that previously treated an unavailable country as no result. */
    public Country searchCountry(String countryName) {
        try { return getCountry(countryName); }
        catch (IOException e) { return null; }
    }

    public Country getCountry(String nameOrCode) throws IOException {
        String query;
        try { query = util.InputValidation.keyword(nameOrCode, "Tên quốc gia", 1, 200); }
        catch (util.InputValidation.ValidationException error) { throw new IOException(error.getMessage()); }
        String alias = NAME_CODES.get(normalize(query));
        if (alias != null) return getByCode(alias);
        if (query.matches("[A-Za-z]{2,3}")) return getByCode(query);
        return lookup("name:" + query.toLowerCase(Locale.ROOT), "/name/" + encode(query), query);
    }

    public Country getByCode(String code) throws IOException {
        if (code == null || !code.trim().matches("[A-Za-z]{2,3}")) {
            throw new IOException("Mã quốc gia không hợp lệ.");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return lookup("code:" + normalized, "/alpha/" + normalized, null);
    }

    private Country lookup(String cacheKey, String path, String exactName) throws IOException {
        CachedCountry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt > now.getAsLong()) return cached.country;
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + path))
                .timeout(Duration.ofSeconds(4)).header("User-Agent", "TraCuuThongTinDiaLy/1.0")
                .GET().build();
        try {
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 404) throw new IOException("Không tìm thấy quốc gia. Hãy nhập tên tiếng Anh đầy đủ hoặc mã ISO.");
            if (status == 429) throw new IOException("API quốc gia vượt giới hạn yêu cầu (HTTP 429). Hãy thử lại sau.");
            if (status < 200 || status >= 300) throw new IOException("API quốc gia lỗi HTTP " + status + ".");
            JsonElement parsed = JsonParser.parseString(response.body());
            JsonObject selected;
            if (parsed.isJsonObject()) {
                selected = parsed.getAsJsonObject();
            } else {
                JsonArray countries = parsed.getAsJsonArray();
                if (countries.isEmpty()) throw new IOException("API quốc gia không có kết quả.");
                selected = countries.size() == 1 ? countries.get(0).getAsJsonObject() : null;
                if (selected == null && exactName != null) {
                    for (JsonElement value : countries) {
                        JsonObject candidate = value.getAsJsonObject();
                        if (normalize(countryName(candidate)).equals(normalize(exactName))) {
                            if (selected != null) throw new IOException("Tên quốc gia có nhiều kết quả. Hãy dùng mã ISO.");
                            selected = candidate;
                        }
                    }
                }
                if (selected == null) throw new IOException("Tên quốc gia có nhiều kết quả. Hãy dùng mã ISO để chọn chính xác.");
            }
            Country country = parseCountry(selected);
            if (country.getHasLandBorders() == null) enrichBorders(country);
            if (cache.size() >= 300) cache.clear();
            // Retry unavailable optional borders soon, while avoiding an extra HTTP call on every lookup.
            long ttl = country.getHasLandBorders() == null ? INCOMPLETE_BORDER_CACHE_MILLIS : CACHE_MILLIS;
            cache.put(cacheKey, new CachedCountry(country, now.getAsLong() + ttl));
            return country;
        } catch (HttpTimeoutException e) {
            throw new IOException("Hết thời gian chờ API quốc gia.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Đã hủy yêu cầu quốc gia.");
        } catch (RuntimeException e) {
            throw new IOException("API quốc gia trả về dữ liệu thiếu hoặc không hợp lệ.");
        }
    }

    private static Country parseCountry(JsonObject json) throws IOException {
        String countryName = countryName(json);
        if (countryName.isEmpty()) throw new IOException("API quốc gia thiếu tên quốc gia.");
        List<String> currencies = new ArrayList<>();
        JsonElement currencyField = json.get("currencies");
        if (currencyField != null && currencyField.isJsonArray()) {
            for (JsonElement value : currencyField.getAsJsonArray()) {
                if (value.isJsonObject()) addText(currencies, currency(value.getAsJsonObject(), string(value.getAsJsonObject(), "code")));
            }
        } else if (currencyField != null && currencyField.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : currencyField.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonObject()) addText(currencies, currency(entry.getValue().getAsJsonObject(), entry.getKey()));
            }
        }
        List<String> languages = new ArrayList<>();
        JsonElement languageField = json.get("languages");
        if (languageField != null && languageField.isJsonArray()) {
            for (JsonElement value : languageField.getAsJsonArray()) {
                addText(languages, value.isJsonObject() ? string(value.getAsJsonObject(), "name") : primitiveText(value));
            }
        } else if (languageField != null && languageField.isJsonObject()) {
            for (JsonElement value : languageField.getAsJsonObject().asMap().values()) addText(languages, primitiveText(value));
        }
        List<String> capitals = arrayOrString(json.get("capital"));
        String code = string(json, "alpha2Code");
        if (code.isEmpty()) code = string(json, "cca2");
        Country country = new Country(0, countryName, code, String.join(", ", capitals),
                String.join(", ", currencies), String.join(", ", languages),
                flagImage(json, code), "", "");
        country.setPrimaryCapital(capitals.isEmpty() ? "" : capitals.get(0));
        country.setPopulation(population(json.get("population")));
        Double area = number(json.get("area"));
        if (area != null && area >= 0) country.setArea(area);
        country.setRegion(string(json, "region"));
        country.setSubregion(string(json, "subregion"));
        country.setTimezones(joinedArray(json, "timezones"));
        Double[] center = coordinates(json, "latlng");
        country.setLatitude(center[0]);
        country.setLongitude(center[1]);
        Double[] capital = coordinates(object(json, "capitalInfo"), "latlng");
        country.setCapitalLatitude(capital[0]);
        country.setCapitalLongitude(capital[1]);
        JsonElement borders = json.get("borders");
        if (borders != null && borders.isJsonArray()) {
            List<String> codes = new ArrayList<>();
            for (JsonElement value : borders.getAsJsonArray()) {
                String border = primitiveText(value);
                if (border.matches("[A-Za-z]{3}")) codes.add(border.toUpperCase(Locale.ROOT));
            }
            country.setQuocGiaLienKe(String.join(", ", codes));
            if (borders.getAsJsonArray().isEmpty()) country.setHasLandBorders(false);
            else if (!codes.isEmpty()) country.setHasLandBorders(true);
        }
        return country;
    }

    private static String flagImage(JsonObject json, String code) {
        String png = string(object(json, "flags"), "png");
        if (!png.isEmpty()) return png;
        // The v2 country API supplies SVG; Swing ImageIO needs a raster image.
        // Flagpedia publishes PNG flags under the ISO alpha-2 country code.
        return code.matches("[A-Za-z]{2}")
                ? "https://flagcdn.com/w320/" + code.toLowerCase(Locale.ROOT) + ".png" : "";
    }

    /** countries.dev can omit borders for islands; confirm through its documented neighbours API. */
    private void enrichBorders(Country country) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/borders/" + encode(country.getTenQuocGia())))
                .timeout(BORDER_TIMEOUT).header("User-Agent", "TraCuuThongTinDiaLy/1.0").GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // The live API uses this specific 404 message for known island countries, not an empty array.
            if (response.statusCode() == 404 && noBordersResponse(response.body())) {
                country.setHasLandBorders(false);
                country.setQuocGiaLienKe("");
                return;
            }
            if (response.statusCode() != 200) return;
            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonArray()) return;
            JsonArray neighbors = parsed.getAsJsonArray();
            if (neighbors.isEmpty()) {
                country.setHasLandBorders(false);
                country.setQuocGiaLienKe("");
                return;
            }
            List<String> borders = new ArrayList<>();
            for (JsonElement value : neighbors) {
                if (!value.isJsonObject()) return;
                JsonObject neighbor = value.getAsJsonObject();
                String neighborCode = string(neighbor, "alpha2Code");
                if (neighborCode.isEmpty()) neighborCode = string(neighbor, "cca2");
                if (country.getMaQuocGia().equalsIgnoreCase(neighborCode)) return;
                String code = string(neighbor, "alpha3Code");
                if (code.isEmpty()) code = string(neighbor, "cca3");
                String label = code.matches("[A-Za-z]{3}") ? code.toUpperCase(Locale.ROOT) : countryName(neighbor);
                if (label.isEmpty()) return;
                if (!borders.contains(label)) borders.add(label);
            }
            country.setQuocGiaLienKe(String.join(", ", borders));
            country.setHasLandBorders(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException ignored) {
            // Optional enrichment must never discard valid population, capital or other country metadata.
        }
    }

    private static boolean noBordersResponse(String body) {
        String message = body.trim();
        if (message.equalsIgnoreCase("No bordering countries found")) return true;
        try {
            JsonElement parsed = JsonParser.parseString(message);
            return parsed.isJsonObject() && string(parsed.getAsJsonObject(), "error")
                    .equalsIgnoreCase("No bordering countries found");
        } catch (RuntimeException e) { return false; }
    }

    private static Double[] coordinates(JsonObject json, String field) {
        if (!present(json, field) || !json.get(field).isJsonArray()) return new Double[]{null, null};
        JsonArray values = json.getAsJsonArray(field);
        if (values.size() < 2) return new Double[]{null, null};
        Double lat = number(values.get(0)), lon = number(values.get(1));
        if (lat == null || lon == null || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            return new Double[]{null, null};
        }
        return new Double[]{lat, lon};
    }

    private static String joinedArray(JsonObject json, String field) {
        return json == null ? "" : String.join(", ", arrayOrString(json.get(field)));
    }

    private static List<String> arrayOrString(JsonElement field) {
        List<String> values = new ArrayList<>();
        if (field != null && field.isJsonArray()) {
            for (JsonElement value : field.getAsJsonArray()) addText(values, primitiveText(value));
        } else addText(values, primitiveText(field));
        return values;
    }
    private static String countryName(JsonObject json) {
        if (!present(json, "name")) return "";
        if (json.get("name").isJsonPrimitive()) return primitiveText(json.get("name"));
        JsonObject name = object(json, "name");
        String common = string(name, "common");
        return common.isEmpty() ? string(name, "official") : common;
    }
    private static String currency(JsonObject json, String code) {
        String name = string(json, "name"), symbol = string(json, "symbol");
        if (name.isEmpty() && code.isEmpty()) return "";
        String details = code + (symbol.isEmpty() ? "" : (code.isEmpty() ? "" : ", ") + symbol);
        return name + (details.isEmpty() ? "" : (name.isEmpty() ? "" : " ") + "(" + details + ")");
    }

    private static boolean present(JsonObject json, String field) { return json != null && json.has(field) && !json.get(field).isJsonNull(); }
    private static String string(JsonObject json, String field) { return json == null ? "" : primitiveText(json.get(field)); }
    private static String primitiveText(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString().trim() : "";
    }
    private static void addText(List<String> values, String text) { if (!text.isEmpty()) values.add(text); }
    private static JsonObject object(JsonObject json, String field) {
        return json != null && json.has(field) && json.get(field).isJsonObject() ? json.getAsJsonObject(field) : null;
    }
    private static Double number(JsonElement value) {
        try {
            if (value == null || !value.isJsonPrimitive() || value.getAsJsonPrimitive().isBoolean()) return null;
            double number = value.getAsDouble();
            return Double.isFinite(number) ? number : null;
        } catch (RuntimeException e) { return null; }
    }
    private static Long population(JsonElement value) {
        try {
            if (value == null || !value.isJsonPrimitive() || value.getAsJsonPrimitive().isBoolean()) return null;
            long population = new java.math.BigDecimal(value.getAsString()).longValueExact();
            return population >= 0 ? population : null;
        } catch (RuntimeException e) { return null; }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replaceAll("\\s+", " ");
    }
    private static final class CachedCountry {
        final Country country;
        final long expiresAt;
        CachedCountry(Country country, long expiresAt) { this.country = country; this.expiresAt = expiresAt; }
    }
}
