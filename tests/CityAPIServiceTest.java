package service;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import model.Location;
import model.Message;
import java.io.IOException;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class CityAPIServiceTest {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--live".equals(args[0])) {
            CityAPIService api = new CityAPIService();
            for (String keyword : new String[]{"Hanoi", "Ho Chi Minh City", "Paris"}) {
                List<Location> cities = api.search(keyword);
                check(!cities.isEmpty(), "No live result for " + keyword);
                Location city = cities.get(0);
                JsonObject details = api.getDetails(city);
                System.out.println(city.getCityName() + ", " + city.getCountry()
                        + ": dân số=" + city.getPopulation() + ", có thời tiết=" + details.has("weather")
                        + ", địa điểm=" + details.getAsJsonArray("places").size());
                if (details.has("weatherError")) System.out.println(details.get("weatherError"));
                if (details.has("placesError")) System.out.println(details.get("placesError"));
                int images = 0, descriptions = 0;
                for (JsonElement item : details.getAsJsonArray("places")) {
                    JsonObject place = item.getAsJsonObject();
                    if (place.has("imageUrl")) images++;
                    if (place.has("description") && !place.get("description").getAsString().isBlank()) descriptions++;
                }
                System.out.println("Ảnh Wikipedia=" + images + ", mô tả=" + descriptions);
                if (details.has("placesMetadataError")) System.out.println(details.get("placesMetadataError"));
            }
            return;
        }
        HttpServer fixture = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        AtomicReference<String> weatherQuery = new AtomicReference<>();
        AtomicReference<String> placesQuery = new AtomicReference<>();
        AtomicReference<String> geoQuery = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger viStatus = new java.util.concurrent.atomic.AtomicInteger(200);
        java.util.concurrent.atomic.AtomicInteger englishCalls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean metadataFailure = new java.util.concurrent.atomic.AtomicBoolean();
        fixture.createContext("/geo", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            geoQuery.set(query);
            int status = query.contains("name=fail") ? 503 : 200;
            String body = query.contains("name=empty") ? "{}" : """
                    {"results":[
                      {"id":1,"name":"Paris","country":"France","admin1":"Île-de-France",
                       "latitude":48.85,"longitude":2.35,"population":2100000,"feature_code":"PPLC"},
                      {"id":2,"name":"Paris","country":"United States",
                       "latitude":33.66,"longitude":-95.55,"feature_code":"PPL"},
                      {"id":3,"name":"Not a city","latitude":0,"longitude":0,"feature_code":"MT"}
                    ]}
                    """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        fixture.createContext("/weather", exchange -> {
            weatherQuery.set(exchange.getRequestURI().getQuery());
            byte[] bytes = """
                    {"dt":1791194400,"main":{"temp":18.5,"humidity":65},
                    "weather":[{"description":"mây bao phủ"}],"wind":{"speed":2.4}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        fixture.createContext("/places", exchange -> {
            boolean metadata = exchange.getRequestURI().getQuery().contains("pageids=");
            if (!metadata) placesQuery.set(exchange.getRequestURI().getQuery());
            String body = metadata ? """
                    {"query":{"pages":{"42":{"pageid":42,
                    "thumbnail":{"source":"https://upload.wikimedia.org/example.jpg"},
                    "pageprops":{"wikibase-shortdesc":"Một địa danh lịch sử"}},"43":{"pageid":43}}}}
                    """ : """
                    {"query":{"geosearch":[{"pageid":42,"title":"Địa danh lân cận","dist":150},
                    {"pageid":43,"title":"Địa điểm chưa có ảnh","dist":1200}]}}
                    """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(metadata && metadataFailure.get() ? 503 : 200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        fixture.createContext("/invalid", exchange -> {
            byte[] bytes = "not json".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        fixture.createContext("/vi-places", exchange -> {
            byte[] bytes = "{\"query\":{\"geosearch\":[]}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(viStatus.get(), bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        fixture.createContext("/en-places", exchange -> {
            boolean metadata = exchange.getRequestURI().getQuery().contains("pageids=");
            if (!metadata) englishCalls.incrementAndGet();
            String body = metadata
                    ? "{\"query\":{\"pages\":{\"45\":{\"pageid\":45,\"pageprops\":{\"wikibase-shortdesc\":\"An English-only article\"}}}}}"
                    : "{\"query\":{\"geosearch\":[{\"pageid\":45,\"title\":\"Tower Place\",\"dist\":50}]}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        fixture.start();
        try {
            String base = "http://localhost:" + fixture.getAddress().getPort();
            CityAPIService api = new CityAPIService(base + "/geo",
                    new WeatherAPIService(base + "/geo", base + "/weather", () -> "fixture-key"), base + "/places");
            List<Location> cities = api.search("Paris");
            check(geoQuery.get().contains("language=vi"), "City API must request Vietnamese place metadata");
            check(cities.size() == 2, "Must filter non-city features");
            check(cities.get(0).getPopulation() == 2100000L, "Population mapping");
            check(cities.get(1).getPopulation() == null, "Missing population must stay unknown");
            check(!cities.get(0).getCountry().equals(cities.get(1).getCountry()), "Ambiguous city countries");
            JsonObject details = api.getDetails(cities.get(1));
            check(details.getAsJsonObject("weather").get("temp").getAsDouble() == 18.5, "Weather mapping");
            check(details.getAsJsonArray("places").size() == 2, "Nearby places mapping");
            JsonObject landmark = details.getAsJsonArray("places").get(0).getAsJsonObject();
            check(landmark.get("imageUrl").getAsString().startsWith("https://upload.wikimedia.org/"), "Thumbnail mapping");
            check(landmark.get("description").getAsString().equals("Một địa danh lịch sử"), "Vietnamese description mapping");
            check(landmark.get("name").getAsString().equals("Địa danh lân cận"), "Preserve Vietnamese article title");
            check(landmark.get("url").getAsString().equals(base + "/?curid=42"), "Article links must use the actual queried wiki origin");
            check(!details.getAsJsonArray("places").get(1).getAsJsonObject().has("imageUrl"), "Missing image is optional");
            metadataFailure.set(true);
            JsonObject fallback = api.getDetails(cities.get(1));
            check(fallback.getAsJsonArray("places").size() == 2 && fallback.has("placesMetadataError")
                    && !fallback.has("placesError"), "Metadata failure must preserve nearby places");
            check(fallback.get("placesMetadataError").getAsString().contains("HTTP 503"), "Keep explicit API failure details");
            metadataFailure.set(false);
            check(weatherQuery.get().contains("lat=33.66") && weatherQuery.get().contains("lon=-95.55"),
                    "Weather must use selected city's coordinates");
            check(weatherQuery.get().contains("lang=vi"), "City weather descriptions must request Vietnamese");
            check(placesQuery.get().contains("gscoord=33.66|-95.55"), "Places must use selected coordinates");
            check(api.search("empty").isEmpty(), "No results must be an empty list");
            try { api.search("fail"); throw new AssertionError("HTTP error accepted"); }
            catch (IOException expected) { }
            try { api.search("x"); throw new AssertionError("Short query accepted"); }
            catch (IOException expected) { }
            CityAPIService failing = new CityAPIService(base + "/invalid",
                    new WeatherAPIService(base + "/invalid", base + "/invalid", () -> "fixture-key"), base + "/invalid");
            JsonObject partial = failing.getDetails(cities.get(0));
            check(!partial.has("weather") && partial.has("weatherError") && partial.has("placesError"),
                    "API failures must be visible; must not fabricate weather");

            WeatherAPIService weatherApi = new WeatherAPIService(base + "/geo", base + "/weather", () -> "fixture-key");
            CityAPIService localized = new CityAPIService(base + "/geo", weatherApi, base + "/vi-places", base + "/en-places");
            JsonObject englishFallback = localized.getDetails(cities.get(0));
            check(englishFallback.has("placesNotice") && englishFallback.get("placesNotice").getAsString().contains("tiếng Anh"),
                    "English fallback must be clearly identified in Vietnamese");
            JsonObject englishPlace = englishFallback.getAsJsonArray("places").get(0).getAsJsonObject();
            check(englishPlace.get("name").getAsString().equals("Tower Place")
                    && englishPlace.get("description").getAsString().equals("An English-only article"),
                    "Fallback source names/descriptions must not be replaced with fabricated translations");
            int beforeFallback = englishCalls.get();
            viStatus.set(503);
            JsonObject viFailure = localized.getDetails(cities.get(0));
            check(viFailure.has("placesError") && viFailure.get("placesError").getAsString().contains("HTTP 503")
                    && englishCalls.get() == beforeFallback, "Vietnamese API failures must stay visible without silent fallback");
            viStatus.set(200);
            CityAPIService populatedVi = new CityAPIService(base + "/geo", weatherApi, base + "/places", base + "/en-places");
            check(!populatedVi.getDetails(cities.get(0)).has("placesNotice") && englishCalls.get() == beforeFallback,
                    "Existing Vietnamese places must not trigger English lookup");
            Field defaultPlaces = CityAPIService.class.getDeclaredField("placesUrl");
            defaultPlaces.setAccessible(true);
            check(defaultPlaces.get(new CityAPIService()).equals("https://vi.wikipedia.org/w/api.php"),
                    "Production Wikipedia lookup must prefer Vietnamese site");

            // Exercise actual request routing without MySQL: no database calls in this flow.
            Class<?> handlerClass = Class.forName("ClientHandler");
            Object handler = handlerClass.getConstructor(Socket.class, KeyPair.class)
                    .newInstance(new Socket(), null);
            Field apiField = handlerClass.getDeclaredField("cityAPIService");
            apiField.setAccessible(true);
            apiField.set(handler, api);
            Method request = handlerClass.getDeclaredMethod("handleRequest", String.class);
            request.setAccessible(true);
            JsonObject search = new JsonObject();
            search.addProperty("keyword", "Paris");
            JsonObject response = JsonParser.parseString((String) request.invoke(handler,
                    new Message("SEARCH_LOCATION", search).toJson())).getAsJsonObject();
            check(response.get("action").getAsString().equals("SEARCH_LOCATION_RESPONSE"), "Search route");
            check(response.getAsJsonArray("data").size() == 2, "Search must return API cities");
            JsonObject selected = new JsonObject();
            selected.addProperty("id", 2);
            response = JsonParser.parseString((String) request.invoke(handler,
                    new Message("GET_CITY_DETAILS", selected).toJson())).getAsJsonObject();
            check(response.getAsJsonObject("data").has("weather"), "Details route");
            selected.addProperty("id", 999);
            response = JsonParser.parseString((String) request.invoke(handler,
                    new Message("GET_CITY_DETAILS", selected).toJson())).getAsJsonObject();
            check(response.get("action").getAsString().equals("ERROR"), "Unknown selected city rejected");
            System.out.println("ĐẠT: tra cứu API và xử lý yêu cầu không cần CSDL, quốc gia/tọa độ/dân số, thời tiết/địa điểm, kết quả rỗng và lỗi");
        } finally { fixture.stop(0); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
