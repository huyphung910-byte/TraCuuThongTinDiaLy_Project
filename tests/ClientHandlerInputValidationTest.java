import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import dao.HistoryDAO;
import dao.HotelDAO;
import dao.LocationDAO;
import dao.UserDAO;
import dao.WeatherSubscriptionDAO;
import model.Country;
import model.Hotel;
import model.Location;
import model.Message;
import model.User;
import model.WeatherData;
import server.ClientManager;
import server.ClientSession;
import service.CityAPIService;
import service.CountryAPIService;
import service.CountryDetailsService;
import service.WeatherAPIService;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline request boundary checks. Fake DAOs and loopback APIs never access MySQL or external APIs. */
public final class ClientHandlerInputValidationTest {
    private static final Gson GSON = new com.google.gson.GsonBuilder().serializeNulls().create();
    private static final int USER_ID = 77;

    public static void main(String[] args) throws Exception {
        try (ApiFixture api = new ApiFixture()) {
            rejectsMalformedEnvelope(api);
            authenticationPrecedesPersonalPayloadValidation(api);
            validatesAccountsBeforeAccessAndClearsFailedLogin(api);
            rejectsInvalidKeywordsBeforeAnyApiOrDao(api);
            rejectsInvalidCoordinatesBeforePersistence(api);
            rejectsInvalidIdsBeforeAccess(api);
            forwardsOnlyNormalizedFieldsWithServerIdentity(api);
        }
        System.out.println("ClientHandlerInputValidationTest: tất cả kiểm tra đầu vào phía máy chủ đã đạt.");
    }

    private static void rejectsMalformedEnvelope(ApiFixture api) throws Exception {
        try (Harness h = new Harness(api)) {
            for (String value : new String[]{"null", "[]", "42", "\"LOGIN\""}) {
                rejectedRaw(h, value, "đối tượng JSON");
            }
            for (String value : new String[]{"{}", "{\"action\":null}", "{\"action\":\"  \"}"}) {
                rejectedRaw(h, value, "thiếu tên thao tác");
            }
            for (String value : new String[]{"17", "false", "[]", "{}"}) {
                rejectedRaw(h, "{\"action\":" + value + "}", "phải là chuỗi");
            }
            rejectedRaw(h, "{\"action\":", "JSON");
        }
    }

    private static void authenticationPrecedesPersonalPayloadValidation(ApiFixture api) throws Exception {
        try (Harness h = new Harness(api)) {
            for (String action : new String[]{"GET_PROFILE", "UPDATE_PROFILE", "CHANGE_PASSWORD", "SAVE_LOCATION",
                    "GET_SAVED_LOCATIONS", "DELETE_LOCATION", "SAVE_HISTORY", "GET_HISTORY",
                    "SUBSCRIBE_WEATHER", "GET_SUBSCRIPTIONS"}) {
                rejected(h, action, "[1,2]", "đăng nhập");
                rejected(h, action, "{\"userId\":77,\"id\":77}", "đăng nhập");
                check(h.identity() == null, "Spoofed ids cannot establish authentication");
            }
        }
    }

    private static void validatesAccountsBeforeAccessAndClearsFailedLogin(ApiFixture api) throws Exception {
        try (Harness h = new Harness(api)) {
            for (String action : new String[]{"REGISTER", "LOGIN"}) {
                for (String username : new String[]{"\"\"", "\"  \"", "123", "[]", "{}", "null",
                        GSON.toJson("u".repeat(51)), "\"two users\""}) {
                    rejected(h, action, account(username, "\"Secret 8\"", "\"An\"", "\"an@example.com\""), "Tên đăng nhập");
                }
                for (String password : new String[]{"\"short\"", "\"        \"", "123", "null", "{}",
                        GSON.toJson("p".repeat(129)), "\"secret\\n8\""}) {
                    rejected(h, action, account("\"alice\"", password, "\"An\"", "\"an@example.com\""), "Mật khẩu");
                }
            }
            for (String email : new String[]{"\"\"", "\"an@@example.com\"", "\"an@example\"", "\"a..n@example.com\"",
                    "\"an@example..com\"", "true", "[]", GSON.toJson("a".repeat(91) + "@example.com")}) {
                rejected(h, "REGISTER", account("\"alice\"", "\"Secret 8\"", "\"An\"", email), "Email");
            }
            rejected(h, "REGISTER", account("\"alice\"", "\"Secret 8\"", "\"\"", "\"an@example.com\""), "Họ tên");
            rejected(h, "REGISTER", account("\"alice\"", "\"Secret 8\"", GSON.toJson("a".repeat(101)), "\"an@example.com\""), "Họ tên");
            for (String payload : new String[]{"null", "[]", "{}", "{\"username\":123,\"password\":\"Secret 8\"}",
                    "{\"username\":\"alice\",\"password\":\"short\"}"}) {
                h.login();
                rejected(h, "LOGIN", payload, null);
                check(h.identity() == null && ClientManager.getSession(USER_ID) == null,
                        "Every malformed login must remove the previous authenticated identity");
            }
            h.login();
            rejected(h, "UPDATE_PROFILE", "{\"fullname\":[],\"email\":\"an@example.com\"}", "Họ tên");
            rejected(h, "UPDATE_PROFILE", "{\"fullname\":\"An\",\"email\":\"bad\"}", "Email");
            rejected(h, "CHANGE_PASSWORD", "{\"currentPassword\":\"short\",\"newPassword\":\"NewSecret8\"}", "Mật khẩu hiện tại");
            rejected(h, "CHANGE_PASSWORD", "{\"currentPassword\":\"Secret 8\",\"newPassword\":\"short\"}", "Mật khẩu mới");
            rejected(h, "CHANGE_PASSWORD", "{\"currentPassword\":\"Secret 8\",\"newPassword\":\"Secret 8\"}", "phải khác");
        }
    }

    private static void rejectsInvalidKeywordsBeforeAnyApiOrDao(ApiFixture api) throws Exception {
        try (Harness h = new Harness(api)) {
            h.login();
            String[][] cases = {{"SEARCH_LOCATION", "keyword", "Tên thành phố"},
                    {"SEARCH_COMPARISON_CITIES", "keyword", "Tên thành phố"},
                    {"SEARCH_COUNTRY", "countryName", "Tên quốc gia"},
                    {"GET_WEATHER", "cityName", "Tên thành phố"}, {"GET_HOTELS", "cityName", "Tên thành phố"},
                    {"SAVE_HISTORY", "keyword", "Từ khóa lịch sử"}};
            for (String[] entry : cases) {
                int maximum = entry[0].equals("SAVE_HISTORY") ? 255 : 200;
                for (String keyword : new String[]{"\"\"", "\" \\u00a0 \"", "123", "false", "null", "[]", "{}",
                        GSON.toJson("x".repeat(maximum + 1)), "\"Hà\\nNội\""}) {
                    rejected(h, entry[0], "{\"" + entry[1] + "\":" + keyword + "}", entry[2]);
                }
                rejected(h, entry[0], "{}", entry[2]);
                rejected(h, entry[0], "\"   \"", entry[0].equals("SAVE_HISTORY") ? "đối tượng JSON" : entry[2]);
                rejected(h, entry[0], "42", "đối tượng JSON");
            }
            rejected(h, "COMPARE_COUNTRIES", "{\"leftName\":\"\",\"rightName\":\"JP\"}", "Quốc gia thứ nhất");
            rejected(h, "COMPARE_COUNTRIES", "{\"leftName\":\"VN\",\"rightName\":[]}", "Quốc gia thứ hai");
            rejected(h, "COMPARE_COUNTRIES", "{\"leftName\":\"VN\",\"rightName\":" + GSON.toJson("x".repeat(201)) + "}", "Quốc gia thứ hai");
        }
    }

    private static void rejectsInvalidCoordinatesBeforePersistence(ApiFixture api) throws Exception {
        try (Harness h = new Harness(api)) {
            h.login();
            String[] invalidPairs = {"", ",\"lat\":0", ",\"lng\":0", ",\"lat\":null,\"lng\":null", ",\"lat\":null,\"lng\":0",
                    ",\"lat\":0,\"lng\":null", ",\"lat\":90.01,\"lng\":0", ",\"lat\":-90.01,\"lng\":0",
                    ",\"lat\":0,\"lng\":180.01", ",\"lat\":0,\"lng\":-180.01",
                    ",\"lat\":\"21.03\",\"lng\":105.85", ",\"lat\":0,\"lng\":\"105\"",
                    ",\"lat\":[],\"lng\":0", ",\"lat\":false,\"lng\":0",
                    ",\"lat\":1e999,\"lng\":0", ",\"lat\":0,\"lng\":-1e999",
                    ",\"lat\":NaN,\"lng\":0", ",\"lat\":0,\"lng\":Infinity"};
            for (String pair : invalidPairs) {
                String field = pair.equals(",\"lat\":0") || pair.startsWith(",\"lat\":0,") ? "Kinh độ" : "Vĩ độ";
                rejected(h, "SAVE_LOCATION", "{\"name\":\"Hà Nội\"" + pair + "}",
                        field);
                if (!pair.isEmpty()) {
                    rejected(h, "SUBSCRIBE_WEATHER", "{\"location\":\"Hà Nội\",\"condition\":\"Mưa\"" + pair + "}",
                            field);
                }
            }
            for (String coords : new String[]{",\"lat\":-90,\"lng\":-180", ",\"lat\":90,\"lng\":180", ",\"lat\":0,\"lng\":0"}) {
                success(h.send("SAVE_LOCATION", "{\"name\":\"Địa điểm\"" + coords + "}"), "SAVE_LOCATION_RESPONSE");
                check(h.store.last().owner == USER_ID, "Boundary coordinates still belong to the server-side user");
            }
        }
    }

    private static void rejectsInvalidIdsBeforeAccess(ApiFixture api) throws Exception {
        try (Harness h = new Harness(api)) {
            h.login();
            for (String id : new String[]{"null", "0", "-1", "1.5", "2147483648", "1e999", "\"1\"", "false", "[]", "{}"}) {
                rejected(h, "GET_CITY_DETAILS", "{\"id\":" + id + "}", "Mã địa điểm");
                rejected(h, "DELETE_LOCATION", "{\"id\":" + id + ",\"userId\":88}", "Mã địa điểm");
                rejected(h, "COMPARE_CITIES", "{\"leftId\":" + id + ",\"rightId\":1}", "Mã thành phố thứ nhất");
                rejected(h, "COMPARE_CITIES", "{\"leftId\":1,\"rightId\":" + id + "}", "Mã thành phố thứ hai");
            }
            rejected(h, "GET_CITY_DETAILS", "{}", "Mã địa điểm");
            rejected(h, "COMPARE_CITIES", "{\"leftId\":1}", "Mã thành phố thứ hai");
            rejected(h, "GET_CITY_DETAILS", "{\"id\":1}", "Hãy tra cứu");
            success(h.send("DELETE_LOCATION", "{\"id\":3,\"userId\":[88]}"), "DELETE_LOCATION_RESPONSE");
            check(h.store.last().owner == USER_ID && h.store.last().values.get(0).equals(3),
                    "Validated record id is forwarded with the authenticated owner, never the client's userId");
        }
    }

    private static void forwardsOnlyNormalizedFieldsWithServerIdentity(ApiFixture api) throws Exception {
        try (Harness h = new Harness(api)) {
            String username = "ĐỗKhoa", password = "  bí mật 😀  ";
            JsonObject registration = new JsonObject();
            registration.addProperty("username", " \u00a0" + username + "\u00a0 ");
            registration.addProperty("password", password);
            registration.addProperty("fullname", "  Đỗ Khoa 😀  ");
            registration.addProperty("email", "  khoa@example.com  ");
            registration.add("id", JsonParser.parseString("{\"forged\":88}"));
            registration.add("userId", JsonParser.parseString("[88]"));
            success(h.send("REGISTER", registration.toString()), "REGISTER_RESPONSE");
            check(h.store.username.equals(username) && h.store.password.equals(password)
                            && h.store.fullname.equals("Đỗ Khoa 😀") && h.store.email.equals("khoa@example.com"),
                    "Registration trims Unicode surrounding spaces but preserves every password character");
            action(h.send("LOGIN", registration.toString()), "LOGIN_RESPONSE");
            check(h.identity() == USER_ID, "A real Unicode username and whitespace-sensitive password can log in");
            success(h.send("UPDATE_PROFILE", "{\"fullname\":\"  Nguyễn An  \",\"email\":\"  an@example.com  \",\"id\":88,\"userId\":{\"id\":88}}"), "UPDATE_PROFILE_RESPONSE");
            check(h.store.last().values.equals(List.of("Nguyễn An", "an@example.com")), "Only validated profile fields reach the DAO");
            success(h.send("SAVE_HISTORY", "{\"keyword\":\"  Hà Nội  \",\"type\":\"thanh_pho\"}"), "SAVE_HISTORY_RESPONSE");
            check(h.store.last().values.equals(List.of("Hà Nội", "thanh_pho")), "History forwards separate normalized keyword and type");
            success(h.send("SAVE_HISTORY", "{\"keyword\":\"  Việt Nam  \",\"type\":\"quoc_gia\"}"), "SAVE_HISTORY_RESPONSE");
            check(h.store.last().values.equals(List.of("Việt Nam", "quoc_gia")), "Country keyword is stored without a suffix");
            rejected(h, "SAVE_HISTORY", "{\"keyword\":\"Việt Nam\",\"type\":\"country\"}", "Loại tra cứu");
            rejected(h, "SAVE_HISTORY", "{\"keyword\":\"Việt Nam\"}", "Loại tra cứu");
            success(h.send("SAVE_LOCATION", "{\"name\":\"  Hà Nội  \",\"lat\":0,\"lng\":0,\"note\":\"  Ghi chú\\nDu lịch  \",\"userId\":88}"), "SAVE_LOCATION_RESPONSE");
            check(h.store.last().values.equals(List.of("Hà Nội", 0.0, 0.0, "Ghi chú\nDu lịch")),
                    "Genuine zero coordinates and multiline notes are retained after validation");
            success(h.send("SUBSCRIBE_WEATHER", "{\"location\":\"  Hà Nội  \",\"condition\":\"  Mưa lớn  \",\"userId\":88}"), "SUBSCRIBE_WEATHER_RESPONSE");
            Call noCoords = h.store.last();
            check(noCoords.action.equals("SUBSCRIBE_NULLABLE") && noCoords.values.get(1) == null && noCoords.values.get(2) == null,
                    "A name-only subscription must store missing coordinates as NULL, never (0,0)");
            success(h.send("SUBSCRIBE_WEATHER", "{\"location\":\"Hà Nội\",\"condition\":\"Mưa\",\"lat\":0,\"lng\":0}"), "SUBSCRIBE_WEATHER_RESPONSE");
            check(h.store.last().action.equals("SUBSCRIBE_COORDINATES") && h.store.last().values.get(1).equals(0.0),
                    "Explicit zero coordinates dispatch through the compatible primitive overload");
            int apiBefore = api.calls.get();
            action(h.send("SEARCH_LOCATION", "{\"keyword\":\"  Hà Nội  \"}"), "SEARCH_LOCATION_RESPONSE");
            check(api.calls.get() == apiBefore + 1 && api.lastName.equals("Hà Nội"),
                    "A validated Unicode city name reaches only the loopback fixture, normalized correctly");
            action(h.send("GET_HOTELS", "{\"cityName\":\"  Hà Nội  \"}"), "GET_HOTELS_RESPONSE");
            check(h.store.last().values.equals(List.of("Hà Nội")), "Hotel queries receive the same normalized name");
            JsonObject change = new JsonObject();
            change.addProperty("currentPassword", password);
            change.addProperty("newPassword", " new bí mật 😀 ");
            change.addProperty("id", 88);
            success(h.send("CHANGE_PASSWORD", change.toString()), "CHANGE_PASSWORD_RESPONSE");
            check(h.store.password.equals(" new bí mật 😀 "), "Changing a password must not trim its significant spaces");
            for (String read : new String[]{"GET_PROFILE", "GET_SAVED_LOCATIONS", "GET_HISTORY", "GET_SUBSCRIPTIONS"}) {
                action(h.send(read, "{\"userId\":[88],\"id\":88}"), read + "_RESPONSE");
            }
            for (Call call : h.store.calls) {
                if (call.owner != null) check(call.owner == USER_ID, "All personal DAOs use currentUserId: " + call.action);
            }
        }
    }

    private static String account(String username, String password, String fullname, String email) {
        return "{\"username\":" + username + ",\"password\":" + password + ",\"fullname\":" + fullname + ",\"email\":" + email + "}";
    }

    private static void rejected(Harness h, String action, String payload, String fragment) throws Exception {
        int daoBefore = h.store.calls.size(), apiBefore = h.apiCalls();
        error(h.send(action, payload), fragment);
        check(h.store.calls.size() == daoBefore && h.apiCalls() == apiBefore,
                "Invalid " + action + " must be rejected before DAO/API access: " + payload);
    }

    private static void rejectedRaw(Harness h, String raw, String fragment) throws Exception {
        int daoBefore = h.store.calls.size(), apiBefore = h.apiCalls();
        error(h.raw(raw), fragment);
        check(h.store.calls.size() == daoBefore && h.apiCalls() == apiBefore, "Malformed envelopes cannot reach a DAO/API");
    }

    private static void error(JsonObject response, String fragment) {
        action(response, "đăng nhập".equals(fragment) ? "UNAUTHORIZED" : "ERROR");
        String message = response.get("data").getAsString();
        check(!message.isBlank() && (fragment == null || message.contains(fragment)),
                "Expected a specific validation error containing " + fragment + ", got " + response);
        check(!message.equals("Dữ liệu JSON của yêu cầu không hợp lệ.") || "JSON".equals(fragment),
                "Field validation failures must not be hidden behind a generic JSON error");
    }

    private static void action(JsonObject response, String expected) {
        check(response.get("action").getAsString().equals(expected), "Expected " + expected + ", got " + response);
    }

    private static void success(JsonObject response, String expected) {
        action(response, expected);
        check(response.get("data").getAsBoolean(), "Expected success: " + response);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Call(String action, Integer owner, List<Object> values) { }

    private static final class Store {
        final List<Call> calls = new ArrayList<>();
        String username = "alice", password = "Secret 8", fullname = "An", email = "an@example.com";
        void call(String action, Integer owner, Object... values) {
            calls.add(new Call(action, owner, java.util.Arrays.asList(values)));
        }
        Call last() { return calls.get(calls.size() - 1); }
    }

    private static final class Harness implements AutoCloseable {
        final Socket socket = new Socket();
        final ClientHandler handler = new ClientHandler(socket, null);
        final ClientSession session = new ClientSession(null);
        final Store store = new Store();
        final ApiFixture api;
        final AtomicInteger serviceCalls = new AtomicInteger();
        final Method handleRequest;

        Harness(ApiFixture api) throws Exception {
            this.api = api;
            set("session", session);
            set("userDAO", new FakeUserDAO(store));
            set("locationDAO", new FakeLocationDAO(store));
            set("historyDAO", new FakeHistoryDAO(store));
            set("hotelDAO", new FakeHotelDAO(store));
            set("subscriptionDAO", new FakeSubscriptionDAO(store));
            WeatherAPIService weather = new WeatherAPIService() {
                @Override public WeatherData getWeatherData(String name) { serviceCalls.incrementAndGet(); return new WeatherData(); }
                @Override public WeatherData getWeatherData(String name, double lat, double lng) { serviceCalls.incrementAndGet(); return new WeatherData(); }
            };
            set("weatherAPIService", weather);
            set("countryAPIService", new CountryAPIService() {
                @Override public Country getCountry(String query) { serviceCalls.incrementAndGet(); return new Country(); }
                @Override public Country getByCode(String code) { serviceCalls.incrementAndGet(); return new Country(); }
            });
            set("countryDetailsService", new CountryDetailsService() {
                @Override public Country getCountry(String query) { serviceCalls.incrementAndGet(); return new Country(); }
            });
            Constructor<CityAPIService> constructor = CityAPIService.class.getDeclaredConstructor(String.class, WeatherAPIService.class, String.class);
            constructor.setAccessible(true);
            set("cityAPIService", constructor.newInstance(api.endpoint, weather, api.endpoint));
            handleRequest = ClientHandler.class.getDeclaredMethod("handleRequest", String.class);
            handleRequest.setAccessible(true);
        }

        JsonObject send(String action, String payload) throws Exception {
            JsonElement data = payload == null ? null : JsonParser.parseString(payload);
            return raw(GSON.toJson(new Message(action, data)));
        }
        JsonObject raw(String request) throws Exception {
            return JsonParser.parseString((String) handleRequest.invoke(handler, request)).getAsJsonObject();
        }
        void login() throws Exception {
            action(send("LOGIN", account(GSON.toJson(store.username), GSON.toJson(store.password), "\"An\"", "\"an@example.com\"")), "LOGIN_RESPONSE");
            check(identity() == USER_ID, "Fixture credentials must establish the expected server identity");
        }
        Integer identity() throws Exception {
            Field field = ClientHandler.class.getDeclaredField("currentUserId");
            field.setAccessible(true);
            return (Integer) field.get(handler);
        }
        int apiCalls() { return api.calls.get() + serviceCalls.get(); }
        void set(String name, Object value) throws Exception {
            Field field = ClientHandler.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(handler, value);
        }
        @Override public void close() throws Exception {
            send("LOGOUT", null);
            socket.close();
        }
    }

    private static final class ApiFixture implements AutoCloseable {
        final HttpServer server;
        final String endpoint;
        final AtomicInteger calls = new AtomicInteger();
        volatile String lastName;
        ApiFixture() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
            server.createContext("/api", exchange -> {
                calls.incrementAndGet();
                String query = exchange.getRequestURI().getRawQuery();
                if (query != null) for (String pair : query.split("&")) {
                    if (pair.startsWith("name=")) lastName = URLDecoder.decode(pair.substring(5), StandardCharsets.UTF_8);
                }
                byte[] body = "{\"results\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
        }
        @Override public void close() { server.stop(0); }
    }

    private static final class FakeUserDAO extends UserDAO {
        final Store store;
        FakeUserDAO(Store store) { this.store = store; }
        @Override public User checkLogin(String username, String password) {
            store.call("LOGIN", null, username, password);
            return store.username.equals(username) && store.password.equals(password)
                    ? new User(USER_ID, username, store.fullname, store.email) : null;
        }
        @Override public boolean register(String username, String password, String fullname, String email) {
            store.call("REGISTER", null, username, password, fullname, email);
            store.username = username; store.password = password; store.fullname = fullname; store.email = email;
            return true;
        }
        @Override public boolean updateProfile(int id, String fullname, String email) {
            store.call("UPDATE_PROFILE", id, fullname, email);
            store.fullname = fullname; store.email = email;
            return true;
        }
        @Override public User findById(int id) throws SQLException {
            store.call("GET_PROFILE", id);
            return new User(id, store.username, store.fullname, store.email);
        }
        @Override public PasswordChangeResult changePassword(int id, String current, String next) {
            store.call("CHANGE_PASSWORD", id, current, next);
            if (!store.password.equals(current)) return PasswordChangeResult.WRONG_CURRENT_PASSWORD;
            store.password = next;
            return PasswordChangeResult.SUCCESS;
        }
    }

    private static final class FakeLocationDAO extends LocationDAO {
        final Store store;
        FakeLocationDAO(Store store) { this.store = store; }
        @Override public boolean saveLocation(int owner, String name, double lat, double lng, String note) {
            store.call("SAVE_LOCATION", owner, name, lat, lng, note); return true;
        }
        @Override public boolean deleteById(int id, int owner) { store.call("DELETE_LOCATION", owner, id); return true; }
        @Override public List<Location> getByUserId(int owner) { store.call("GET_SAVED_LOCATIONS", owner); return List.of(); }
    }

    private static final class FakeHistoryDAO extends HistoryDAO {
        final Store store;
        FakeHistoryDAO(Store store) { this.store = store; }
        @Override public boolean saveHistory(int owner, String keyword, String type) { store.call("SAVE_HISTORY", owner, keyword, type); return true; }
        @Override public List<model.HistoryEntry> getHistoryByUserId(int owner) { store.call("GET_HISTORY", owner); return List.of(); }
    }

    private static final class FakeHotelDAO extends HotelDAO {
        final Store store;
        FakeHotelDAO(Store store) { this.store = store; }
        @Override public List<Hotel> getHotelsByCity(String city) { store.call("GET_HOTELS", null, city); return List.of(); }
    }

    private static final class FakeSubscriptionDAO extends WeatherSubscriptionDAO {
        final Store store;
        FakeSubscriptionDAO(Store store) { this.store = store; }
        @Override public boolean subscribe(int owner, String location, double lat, double lng, String condition) {
            store.call("SUBSCRIBE_COORDINATES", owner, location, lat, lng, condition); return true;
        }
        @Override public boolean subscribe(int owner, String location, Double lat, Double lng, String condition) {
            store.call("SUBSCRIBE_NULLABLE", owner, location, lat, lng, condition); return true;
        }
        @Override public List<String[]> getByUserId(int owner) { store.call("GET_SUBSCRIPTIONS", owner); return List.of(); }
    }
}
