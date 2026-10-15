package service;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import model.Location;
import model.Message;
import model.Country;
import model.WeatherData;

import java.io.IOException;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

public final class ComparisonServiceTest {
    private static final String VIETNAM = """
            {"name":{"common":"Vietnam"},"cca2":"VN","population":97338583,"area":331212,
             "region":"Asia","subregion":"South-Eastern Asia","capital":["Hanoi"],
             "capitalInfo":{"latlng":[21.03,105.85]},"latlng":[16,108],"timezones":["UTC+07:00"],
             "currencies":{"VND":{"name":"Vietnamese dong","symbol":"₫"}},
             "languages":{"vie":"Vietnamese"},"flags":{"png":"https://flagcdn.com/w320/vn.png"},
             "borders":["CHN","LAO","KHM"]}
            """;
    private static final String JAPAN = """
            {"name":{"common":"Japan"},"cca2":"JP","population":125836021,"area":377930,
             "region":"Asia","subregion":"Eastern Asia","capital":["Tokyo"],
             "capitalInfo":{"latlng":[35.68,139.75]},"latlng":[36,138],"timezones":["UTC+09:00"],
             "currencies":{"JPY":{"name":"Japanese yen","symbol":"¥"}},"languages":{"jpn":"Japanese"}}
            """;
    private static final String UNITED_STATES = """
            {"name":"United States of America","alpha2Code":"US","capital":"Washington, D.C.",
             "population":329484123,"area":9372610,"region":"Americas","subregion":"North America",
             "latlng":[38,-97],"timezones":["UTC-05:00"],"borders":["CAN","MEX"],
             "currencies":[{"code":"USD","name":"United States dollar","symbol":"$"}],
             "languages":[{"name":"English"}],"flags":{"png":"https://flagcdn.com/w320/us.png"}}
            """;
    private static final String JAPAN_V2 = """
            {"name":"Japan","alpha2Code":"JP","capital":"Tokyo","population":125836021,
             "currencies":[{"code":"JPY","name":"Japanese yen"}],"languages":[{"name":"Japanese"}]}
            """;
    private static final String CHINA_V2 = """
            {"name":"China","alpha2Code":"CN","capital":"Beijing","population":1402112000,
             "currencies":[{"code":"CNY","name":"Chinese yuan"}],"languages":[{"name":"Chinese"}]}
            """;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--live-capitals".equals(args[0])) {
            CountryAPIService countries = new CountryAPIService();
            CapitalGeocodingService capitals = new CapitalGeocodingService();
            for (String code : new String[]{"CN", "JP", "US", "VN", "FR"}) {
                Country country = countries.getByCode(code);
                CapitalGeocodingService.Coordinates coords = capitals.resolve(country.getPrimaryCapital(), code,
                        System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3));
                System.out.println(code + " — thủ đô " + country.getPrimaryCapital() + ": tọa độ "
                        + coords.getLatitude() + ", " + coords.getLongitude());
            }
            return;
        }
        if (args.length > 0 && "--live".equals(args[0])) {
            CountryAPIService api = new CountryAPIService();
            for (String name : new String[]{"VN", "Japan", "France", "US", "United States"}) {
                Country country = api.getCountry(name);
                check(country.getPopulation() != null, "Live country population: " + name);
                System.out.println(country.getTenQuocGia() + ": dân số=" + country.getPopulation()
                        + ", thủ đô=" + country.getThuDo() + ", tiền tệ=" + country.getDonViTienTe());
            }
            return;
        }
        HttpServer fixture = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        AtomicInteger metadataCalls = new AtomicInteger();
        AtomicBoolean weatherFailure = new AtomicBoolean();
        AtomicReference<String> capitalQuery = new AtomicReference<>();
        AtomicInteger capitalCalls = new AtomicInteger();
        AtomicBoolean recoverCapital = new AtomicBoolean();
        fixture.createContext("/country-v2", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = path.endsWith("/JP") ? JAPAN_V2 : path.endsWith("/CN") ? CHINA_V2
                    : "{\"name\":\"Unavailable capital\",\"alpha2Code\":\"ZZ\",\"capital\":\"Foreign\",\"population\":123}";
            send(exchange, 200, body);
        });
        fixture.createContext("/capital-geo", exchange -> {
            capitalCalls.incrementAndGet();
            String query = URLDecoder.decode(exchange.getRequestURI().getQuery(), StandardCharsets.UTF_8);
            capitalQuery.set(query);
            String body;
            int status = 200;
            if (query.contains("name=Washington")) {
                check(query.contains("countryCode=US") && !query.contains("D.C."), "Comma qualifier replaced by ISO country filter");
                body = results(candidate("Washington", "US", "PPL", 47, -122),
                        candidate("Washington", "US", "PPLC", 38.895, -77.04));
            } else if (query.contains("name=Tokyo")) {
                check(query.contains("countryCode=JP"), "Japan ISO filter");
                body = results(candidate("Tokyo", "JP", "PPL", 91, 138), "null", "\"malformed distractor\"",
                        candidate("Tokyo", "JP", "PPL", 35, 138),
                        candidate("Tokyo", "US", "PPLC", 1, 1), candidate("Tokyo", "JP", "PPLC", 35.68, 139.75));
            } else if (query.contains("name=Beijing")) {
                check(query.contains("countryCode=CN"), "China ISO filter");
                body = results(candidate("Beijing", "CN", "PPL", 30, 120),
                        candidate("Beijing", "CN", "PPLC", 39.907, 116.397));
            } else if (query.contains("name=Ottawa")) {
                body = results(candidate("Ottawa", "US", "PPLC", 1, 1), candidate("Ottawa", "CA", "PPLA", 45.42, -75.69));
            } else if (query.contains("name=Ambiguous")) {
                body = results(candidate("Ambiguous", "JP", "PPL", 30, 130), candidate("Ambiguous", "JP", "PPL", 31, 131));
            } else if (query.contains("name=Many")) {
                body = results(candidate("Many", "JP", "PPLC", 30, 130), candidate("Many", "JP", "PPLC", 31, 131));
            } else if (query.contains("name=Foreign")) {
                body = results(candidate("Foreign", "US", "PPLC", 1, 1));
            } else if (query.contains("name=Mismatch")) {
                body = results(candidate("Osaka", "JP", "PPL", 34.69, 135.50));
            } else if (query.contains("name=Invalid")) {
                body = results(candidate("Invalid", "JP", "PPLC", 91, 130));
            } else if (query.contains("name=Transient")) {
                body = recoverCapital.get() ? results(candidate("Transient", "JP", "PPLC", 35, 139)) : "{";
            } else if (query.contains("name=Fail")) {
                status = 503; body = "{}";
            } else body = "{}";
            send(exchange, status, body);
        });
        fixture.createContext("/country", exchange -> {
            metadataCalls.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            int status = 200;
            String body;
            if (path.endsWith("/VN")) body = "[" + VIETNAM + "]";
            else if (path.endsWith("/Vietnam")) body = "[" + VIETNAM + "," + JAPAN + "]";
            else if (path.endsWith("/JP") || path.endsWith("/Japan")) body = "[" + JAPAN + "]";
            else if (path.endsWith("/US")) body = UNITED_STATES;
            else if (path.endsWith("/New Zealand")) {
                check(exchange.getRequestURI().getRawPath().contains("New%20Zealand"), "Country-name path encoding");
                body = "[{\"name\":\"New Zealand\",\"alpha2Code\":\"NZ\",\"capital\":\"Wellington\"}]";
            }
            else if (path.endsWith("/ZZ")) body = "[{\"name\":{\"common\":\"Without data\"},\"cca2\":\"ZZ\"}]";
            else if (path.endsWith("/ambiguous")) body = "[" + VIETNAM + "," + JAPAN + "]";
            else if (path.endsWith("/broken")) body = "invalid JSON";
            else { status = 404; body = "{\"status\":404}"; }
            send(exchange, status, body);
        });
        fixture.createContext("/weather", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            check(query.contains("lat=") && query.contains("lon=") && !query.contains("q="),
                    "Weather must use coordinates");
            double temp = query.contains("lon=105.85") ? 30.5 : 20.5;
            send(exchange, weatherFailure.get() ? 401 : 200, "{\"dt\":1791194400,\"main\":{\"temp\":" + temp
                    + ",\"humidity\":65},\"weather\":[{\"description\":\"clouds\"}],\"wind\":{\"speed\":2.4}}");
        });
        fixture.createContext("/geo", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query.contains("q=")) {
                throw new AssertionError("Country comparison must not resolve ambiguous OpenWeather names");
            }
            boolean japan = query.contains("name=Tokyo");
            send(exchange, 200, japan ? """
                    {"results":[{"id":2,"name":"Tokyo","country":"Japan","country_code":"JP",
                     "latitude":35.68,"longitude":139.75,"population":8336599,"feature_code":"PPLC"}]}
                    """ : """
                    {"results":[{"id":1,"name":"Hanoi","country":"Vietnam","country_code":"VN",
                     "latitude":21.03,"longitude":105.85,"population":8053663,"feature_code":"PPLC"}]}
                    """);
        });
        fixture.start();
        try {
            String base = "http://localhost:" + fixture.getAddress().getPort();
            CountryAPIService countries = new CountryAPIService(base + "/country");
            WeatherAPIService weather = new WeatherAPIService(base + "/geo", base + "/weather", () -> "fixture-key");
            CityAPIService cities = new CityAPIService(base + "/geo", weather, base + "/none");
            CapitalGeocodingService capitals = new CapitalGeocodingService(base + "/capital-geo");
            ComparisonService comparison = new ComparisonService(countries, weather, capitals);
            Country vietnam = countries.getCountry("Việt Nam");
            check(vietnam.getPopulation() == 97338583L, "Country population");
            check(vietnam.getArea() == 331212 && vietnam.getCapitalLatitude() == 21.03, "Area and capital coordinates");
            check(vietnam.getDonViTienTe().contains("VND") && vietnam.getNgonNgu().equals("Vietnamese"), "Currency/language");
            check(vietnam.getTimezones().equals("UTC+07:00") && vietnam.getRegion().equals("Asia"), "Geography fields");
            int calls = metadataCalls.get();
            countries.getByCode("VN");
            check(metadataCalls.get() == calls, "Country metadata cache");
            Country missing = countries.getByCode("ZZ");
            check(missing.getPopulation() == null && missing.getArea() == null && missing.getCapitalLatitude() == null,
                    "Missing values must remain unknown");
            fails(() -> countries.getCountry("ambiguous"), "Ambiguous countries must not choose arbitrarily");
            fails(() -> countries.getCountry("broken"), "Malformed API metadata");
            fails(() -> countries.getByCode("XX"), "HTTP 404");
            Country usa = countries.getByCode("US");
            check(usa.getDonViTienTe().contains("USD") && usa.getNgonNgu().equals("English")
                    && usa.getPopulation() == 329484123L, "Production v2 country object schema");
            check(usa.getPrimaryCapital().equals("Washington, D.C.") && usa.getCapitalLatitude() == null,
                    "Commas within one capital name must not split it");
            check(countries.getCountry("United States").getMaQuocGia().equals("US"), "Common country-name alias");
            check(countries.getCountry("New Zealand").getMaQuocGia().equals("NZ"), "Spaces in country path");

            Location hanoi = cities.search("Hanoi").get(0), tokyo = cities.search("Tokyo").get(0);
            check(hanoi.getCountryCode().equals("VN"), "City country code mapping");
            JsonObject result = comparison.compareCities(hanoi, tokyo);
            check(result.getAsJsonObject("left").getAsJsonObject("city").get("population").getAsLong() == 8053663L,
                    "City population must not be replaced with national population");
            check(result.getAsJsonObject("left").getAsJsonObject("weather").get("temp").getAsDouble() == 30.5,
                    "City weather must use selected coordinates");
            check(result.getAsJsonObject("right").getAsJsonObject("country").get("donViTienTe").getAsString().contains("JPY"),
                    "City national context");
            result = comparison.compareCountries("VN", "Japan");
            check(result.getAsJsonObject("left").get("weatherLocation").getAsString().equals("Hanoi"), "Representative capital labelled");
            check(result.getAsJsonObject("right").has("weather"), "Country capital weather");
            result = comparison.compareCountries("US", "JP");
            check(result.getAsJsonObject("left").get("weatherLocation").getAsString().equals("Washington, D.C.")
                    && result.getAsJsonObject("left").has("weather") && capitalQuery.get().contains("countryCode=US"),
                    "Capital without coordinates uses national-capital geocoding then coordinate weather");
            capitalResolverTests(base, weather, capitals, capitalCalls, recoverCapital);
            fails(() -> comparison.compareCities(hanoi, hanoi), "Identical city rejected");
            fails(() -> comparison.compareCountries("VN", "Vietnam"), "Duplicate country aliases rejected");
            fails(() -> comparison.compareCountries("", "JP"), "Empty country rejected");

            result = comparison.compareCountries("XX", "JP");
            check(result.getAsJsonObject("left").has("error") && result.getAsJsonObject("right").has("country"),
                    "One unavailable country preserves other side");
            result = comparison.compareCountries("ZZ", "JP");
            check(result.getAsJsonObject("left").has("country") && result.getAsJsonObject("left").has("weatherError")
                    && !result.getAsJsonObject("left").has("weather"), "Missing capital cannot fabricate country weather");
            weatherFailure.set(true);
            result = comparison.compareCities(hanoi, tokyo);
            check(result.getAsJsonObject("left").has("weatherError") && !result.getAsJsonObject("left").has("weather")
                    && result.getAsJsonObject("right").has("city"), "HTTP401 preserves comparison and exposes failure");
            weatherFailure.set(false);
            ComparisonService noKey = new ComparisonService(countries,
                    new WeatherAPIService(base + "/geo", base + "/weather", () -> ""));
            result = noKey.compareCountries("VN", "JP");
            check(result.getAsJsonObject("left").get("weatherError").getAsString().contains("khóa API")
                    && !result.getAsJsonObject("left").has("weather"), "Missing key error without demo weather");

            routeTests(cities, countries, weather);

            // Verify two slow weather endpoints share one budget, rather than a per-column timeout.
            WeatherAPIService slow = new WeatherAPIService(base + "/geo", base + "/weather", () -> "fixture-key") {
                @Override public WeatherData getWeatherData(String city, double lat, double lon) throws IOException {
                    try { Thread.sleep(9000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    throw new IOException("Fixture slow API");
                }
            };
            long started = System.nanoTime();
            result = new ComparisonService(countries, slow).compareCities(hanoi, tokyo);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            check(elapsedMs < 9500 && result.getAsJsonObject("left").has("weatherError")
                    && result.getAsJsonObject("right").has("country"), "8-second total budget and retained metadata");
            System.out.println("ĐẠT: so sánh thành phố/quốc gia, ánh xạ API thật, thông tin quốc gia, thời tiết thủ đô, "
                    + "đầu vào trùng/không xác định, lỗi từng phần, thiếu khóa, bộ nhớ đệm và thời gian chờ tổng");
        } finally { fixture.stop(0); }
    }

    private static void capitalResolverTests(String base, WeatherAPIService originalWeather, CapitalGeocodingService capitals,
                                             AtomicInteger calls, AtomicBoolean recover) throws Exception {
        java.util.Map<String, String> selected = new java.util.concurrent.ConcurrentHashMap<>();
        WeatherAPIService tracked = new WeatherAPIService(base + "/geo", base + "/weather", () -> "fixture-key") {
            @Override public WeatherData getWeatherData(String city, double lat, double lon) throws IOException {
                selected.put(city, lat + ":" + lon);
                return super.getWeatherData(city, lat, lon);
            }
        };
        CountryAPIService v2Countries = new CountryAPIService(base + "/country-v2");
        ComparisonService v2 = new ComparisonService(v2Countries, tracked, capitals);
        JsonObject result = v2.compareCountries("JP", "CN");
        check(result.getAsJsonObject("left").has("weather") && result.getAsJsonObject("right").has("weather"),
                "Japan/China v2 metadata without capitalInfo still yields weather");
        check(selected.get("Tokyo").equals("35.68:139.75") && selected.get("Beijing").equals("39.907:116.397"),
                "Select same-country national capitals, not first namesakes/foreign candidates");
        int afterFirst = calls.get();
        new ComparisonService(v2Countries, tracked, capitals).compareCountries("JP", "CN");
        check(calls.get() == afterFirst, "Capital cache survives new comparison services");

        result = v2.compareCountries("ZZ", "JP");
        check(result.getAsJsonObject("left").has("country") && result.getAsJsonObject("left").has("weatherError")
                && !result.getAsJsonObject("left").has("weather") && result.getAsJsonObject("right").has("weather"),
                "Unresolvable capital keeps country metadata and other country's weather");
        check(result.getAsJsonObject("left").getAsJsonObject("country").get("population").getAsLong() == 123,
                "Capital-resolution failure preserves population");
        long budget = java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        CapitalGeocodingService.Coordinates ottawa = capitals.resolve("Ottawa", "CA", System.nanoTime() + budget);
        check(ottawa.getLatitude() == 45.42, "Unique exact populated place is valid without PPLC");
        for (String name : new String[]{"Ambiguous", "Many", "Foreign", "Mismatch", "Invalid", "Unknown", "Fail", "Transient"}) {
            fails(() -> capitals.resolve(name, "JP", System.nanoTime() + budget), "Invalid/ambiguous capital must fail: " + name);
        }
        int beforeRecovery = calls.get();
        recover.set(true);
        capitals.resolve("Transient", "JP", System.nanoTime() + budget);
        check(calls.get() == beforeRecovery + 1, "Malformed transient response must not be cached");
        fails(() -> capitals.resolve("Tokyo", "USX", System.nanoTime() + budget), "Invalid ISO filter rejected");
        fails(() -> capitals.resolve("Unknown", "JP", System.nanoTime() - 1), "Expired comparison deadline rejected");

        AtomicLong clock = new AtomicLong(0);
        CapitalGeocodingService timed = new CapitalGeocodingService(base + "/capital-geo", clock::get);
        timed.resolve("Tokyo", "JP", System.nanoTime() + budget);
        int firstCalls = calls.get();
        clock.set(java.time.Duration.ofHours(23).toMillis());
        timed.resolve("Tokyo", "JP", System.nanoTime() + budget);
        check(calls.get() == firstCalls, "Capital coordinates cached for24h");
        clock.set(java.time.Duration.ofHours(24).toMillis());
        timed.resolve("Tokyo", "JP", System.nanoTime() + budget);
        check(calls.get() == firstCalls + 1, "Expired capital coordinates resolved again");

        ComparisonService authoritative = new ComparisonService(new CountryAPIService(base + "/country"), tracked,
                new CapitalGeocodingService(base + "/unreachable"));
        result = authoritative.compareCountries("VN", "JP");
        check(result.getAsJsonObject("left").has("weather") && result.getAsJsonObject("right").has("weather"),
                "Existing authoritative capital coordinates bypass geocoding completely");
    }

    private static String candidate(String name, String code, String feature, double lat, double lon) {
        return "{\"name\":\"" + name + "\",\"country_code\":\"" + code + "\",\"feature_code\":\"" + feature
                + "\",\"latitude\":" + lat + ",\"longitude\":" + lon + "}";
    }
    private static String results(String... candidates) { return "{\"results\":[" + String.join(",", candidates) + "]}"; }

    private static void routeTests(CityAPIService cities, CountryAPIService countries, WeatherAPIService weather) throws Exception {
        Class<?> clazz = Class.forName("ClientHandler");
        Object handler = clazz.getConstructor(Socket.class, KeyPair.class).newInstance(new Socket(), null);
        field(clazz, handler, "cityAPIService", cities);
        field(clazz, handler, "countryAPIService", countries);
        field(clazz, handler, "weatherAPIService", weather);
        Method request = clazz.getDeclaredMethod("handleRequest", String.class);
        request.setAccessible(true);
        JsonObject data = new JsonObject();
        data.addProperty("keyword", "Hanoi");
        sendRequest(request, handler, "SEARCH_LOCATION", data);
        JsonObject first = sendRequest(request, handler, "SEARCH_COMPARISON_CITIES", data);
        check(first.get("action").getAsString().equals("SEARCH_COMPARISON_CITIES_RESPONSE"), "Compare-city search route");
        data.addProperty("keyword", "Tokyo");
        sendRequest(request, handler, "SEARCH_COMPARISON_CITIES", data);
        JsonObject ids = new JsonObject();
        ids.addProperty("leftId", 1);
        ids.addProperty("rightId", 2);
        JsonObject result = sendRequest(request, handler, "COMPARE_CITIES", ids);
        check(result.get("action").getAsString().equals("COMPARE_CITIES_RESPONSE"), "First selected city preserved by second search");
        check(result.getAsJsonObject("data").getAsJsonObject("right").has("weather"), "Compare-city handler weather");
        Field mainCities = clazz.getDeclaredField("searchedCities");
        mainCities.setAccessible(true);
        check(((java.util.Map<?, ?>) mainCities.get(handler)).containsKey(1), "Comparison must preserve regular city selection");
        ids.addProperty("rightId", 999);
        check(sendRequest(request, handler, "COMPARE_CITIES", ids).get("action").getAsString().equals("ERROR"), "Unsearched city rejected");
        JsonObject names = new JsonObject();
        names.addProperty("leftName", "VN");
        names.addProperty("rightName", "JP");
        result = sendRequest(request, handler, "COMPARE_COUNTRIES", names);
        check(result.get("action").getAsString().equals("COMPARE_COUNTRIES_RESPONSE"), "Country comparison route without database calls");
    }

    private static void field(Class<?> clazz, Object target, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static JsonObject sendRequest(Method method, Object target, String action, JsonObject data) throws Exception {
        return JsonParser.parseString((String) method.invoke(target, new Message(action, data).toJson())).getAsJsonObject();
    }
    private static void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
    private static void fails(IOOperation operation, String message) throws Exception {
        try { operation.run(); throw new AssertionError(message); } catch (IOException expected) { }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @FunctionalInterface private interface IOOperation { void run() throws Exception; }
}
