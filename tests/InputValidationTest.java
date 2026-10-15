package util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.Message;

/** Runs with either client or server classes: no database, GUI or external APIs. */
public final class InputValidationTest {
    private static int rejected;

    public static void main(String[] args) {
        accountRules();
        textAndCoordinates();
        strictRequestTypes();
        normalizedRequests();
        System.out.println("ĐẠT: kiểm tra đầu vào, Unicode, kiểu JSON, tọa độ, chuẩn hóa và " + rejected + " trường hợp bị từ chối.");
    }

    private static void accountRules() {
        equal("người_dùng", InputValidation.username("  người_dùng  "));
        equal("x".repeat(50), InputValidation.username("x".repeat(50)));
        equal("😀".repeat(50), InputValidation.username("😀".repeat(50)));
        for (String value : new String[]{null, "", "   ", "\u00a0\u3000", "x".repeat(51), "a b", "a\tb", "a\nb", "a\u0000b", "a\uD800"}) {
            rejects(() -> InputValidation.username(value), "Tên đăng nhập");
        }
        equal("  Abcdef  ", InputValidation.password("  Abcdef  ", "Mật khẩu"));
        equal("😀".repeat(8), InputValidation.password("😀".repeat(8), "Mật khẩu"));
        equal("x".repeat(128), InputValidation.password("x".repeat(128), "Mật khẩu"));
        for (String value : new String[]{null, "", "1234567", "😀".repeat(7), "x".repeat(129), " ".repeat(8), "\u00a0".repeat(8), "abc\ndefgh", "abc\u0000defgh"}) {
            rejects(() -> InputValidation.password(value, "Mật khẩu"), "Mật khẩu");
        }
        equal("Nguyễn Văn An", InputValidation.fullname("  Nguyễn Văn An  "));
        equal("😀".repeat(100), InputValidation.fullname("😀".repeat(100)));
        rejects(() -> InputValidation.fullname("😀".repeat(101)), "Họ tên");
        rejects(() -> InputValidation.fullname("An\tBình"), "Họ tên");
        equal("name+tag@example.com", InputValidation.email("  name+tag@example.com  "));
        equal("first.last@sub.example.com", InputValidation.email("first.last@sub.example.com"));
        equal("a".repeat(64) + "@example.com", InputValidation.email("a".repeat(64) + "@example.com"));
        for (String value : new String[]{null, "", "a@localhost", "a@@example.com", "a..b@example.com", ".a@example.com",
                "a.@example.com", "a@-example.com", "a@example-.com", "a@example..com", "a@exa_mple.com",
                "người@example.com", "a b@example.com", "a@example.com\n", "a@example.123", "a".repeat(65) + "@example.com",
                "a".repeat(64) + "@" + "b".repeat(32) + ".com"}) {
            rejects(() -> InputValidation.email(value), "Email");
        }
    }

    private static void textAndCoordinates() {
        equal("Hà Nội", InputValidation.keyword("\u00a0Hà Nội\u3000", "Từ khóa", 2, 200));
        equal("😀".repeat(200), InputValidation.keyword("😀".repeat(200), "Từ khóa", 1, 200));
        for (String value : new String[]{null, "", "  ", "a", "a".repeat(201), "Hà\nNội", "X\uDFFF"}) {
            rejects(() -> InputValidation.keyword(value, "Từ khóa", 2, 200), "Từ khóa");
        }
        equal("", InputValidation.optionalText(null, "Ghi chú", 2000));
        equal("Dòng 1\nDòng 2\tghi chú", InputValidation.optionalText("Dòng 1\nDòng 2\tghi chú", "Ghi chú", 2000));
        rejects(() -> InputValidation.optionalText("x".repeat(2001), "Ghi chú", 2000), "Ghi chú");
        rejects(() -> InputValidation.optionalText("a\u0000b", "Ghi chú", 2000), "Ghi chú");
        InputValidation.coordinates(-90.0, -180.0);
        InputValidation.coordinates(90.0, 180.0);
        InputValidation.coordinates(0.0, 0.0);
        for (Double latitude : new Double[]{null, -90.0001, 90.0001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            rejects(() -> InputValidation.coordinates(latitude, 0.0), "Vĩ độ");
        }
        for (Double longitude : new Double[]{null, -180.0001, 180.0001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            rejects(() -> InputValidation.coordinates(0.0, longitude), "Kinh độ");
        }
    }

    private static void strictRequestTypes() {
        rejects(() -> RequestValidation.validate(null), "Yêu cầu");
        reject("LOGIN", "null", "JSON");
        reject("LOGIN", "[]", "JSON");
        reject("LOGIN", "\"alice\"", "JSON");
        reject("LOGIN", "{\"username\":123,\"password\":\"12345678\"}", "Tên đăng nhập");
        reject("LOGIN", "{\"username\":\"alice\",\"password\":12345678}", "Mật khẩu");
        reject("REGISTER", "{\"username\":\"alice\",\"password\":\"12345678\",\"fullname\":true,\"email\":\"a@example.com\"}", "Họ tên");
        reject("UPDATE_PROFILE", "{\"fullname\":\"Alice\",\"email\":[\"a@example.com\"]}", "Email");
        reject("CHANGE_PASSWORD", "{\"currentPassword\":\"12345678\",\"newPassword\":\"12345678\"}", "khác");
        for (String action : new String[]{"SEARCH_LOCATION", "SEARCH_COMPARISON_CITIES", "SAVE_HISTORY"}) {
            reject(action, "{\"keyword\":false}", "chuỗi");
            reject(action, "{\"keyword\":\"   \"}", "trống");
        }
        reject("SEARCH_COUNTRY", "123", "JSON");
        reject("SEARCH_COUNTRY", "{\"countryName\":{}}", "chuỗi");
        reject("GET_WEATHER", "{\"cityName\":null}", "thành phố");
        reject("GET_HOTELS", "{\"cityName\":[]}", "chuỗi");
        reject("COMPARE_COUNTRIES", "{\"leftName\":\"VN\",\"rightName\":true}", "thứ hai");
        for (String value : new String[]{"null", "true", "\"7\"", "1.5", "-1", "0", "2147483648", "1e999"}) {
            reject("DELETE_LOCATION", "{\"id\":" + value + "}", "Mã địa điểm");
            reject("GET_CITY_DETAILS", "{\"id\":" + value + "}", "Mã địa điểm");
            reject("COMPARE_CITIES", "{\"leftId\":1,\"rightId\":" + value + "}", "thứ hai");
        }
        reject("SAVE_LOCATION", "{\"name\":\"Hà Nội\"}", "Vĩ độ");
        reject("SAVE_LOCATION", "{\"name\":\"Hà Nội\",\"lat\":0}", "Kinh độ");
        for (String value : new String[]{"null", "true", "\"0\"", "\"NaN\"", "[]", "{}", "1e999", "91", "-91"}) {
            reject("SAVE_LOCATION", "{\"name\":\"Hà Nội\",\"lat\":" + value + ",\"lng\":0}", "Vĩ độ");
        }
        reject("SAVE_LOCATION", "{\"name\":\"Hà Nội\",\"lat\":0,\"lng\":181}", "Kinh độ");
        reject("SAVE_LOCATION", "{\"name\":\"Hà Nội\",\"lat\":0,\"lng\":0,\"note\":false}", "Ghi chú");
        reject("SUBSCRIBE_WEATHER", "{\"location\":\"Tokyo\",\"condition\":\"Mưa\",\"lng\":139}", "Vĩ độ");
        reject("SUBSCRIBE_WEATHER", "{\"location\":\"Tokyo\",\"condition\":\"Mưa\",\"lat\":35}", "Kinh độ");
        reject("SUBSCRIBE_WEATHER", "{\"location\":\"Tokyo\",\"condition\":\"Mưa\",\"lat\":null,\"lng\":null}", "Vĩ độ");
    }

    private static void normalizedRequests() {
        JsonObject original = JsonParser.parseString("{\"username\":\"  alice  \",\"password\":\"  Abcdef  \",\"userId\":999}").getAsJsonObject();
        Message login = RequestValidation.validate(new Message(" login ", original));
        equal("LOGIN", login.getAction());
        JsonObject loginData = (JsonObject) login.getData();
        equal("alice", loginData.get("username").getAsString());
        equal("  Abcdef  ", loginData.get("password").getAsString());
        check(!loginData.has("userId"), "Do not carry client identity into personal actions");
        equal("  alice  ", original.get("username").getAsString());
        equal("VN", data("SEARCH_COUNTRY", "\" VN \"").get("countryName").getAsString());
        equal("Paris", data("SEARCH_LOCATION", "\" Paris \"").get("keyword").getAsString());
        equal(7, data("GET_CITY_DETAILS", "{\"id\":7.0}").get("id").getAsInt());
        JsonObject saved = data("SAVE_LOCATION", "{\"name\":\"  Hà Nội  \",\"lat\":0,\"lng\":0,\"note\":null,\"userId\":999}");
        equal("Hà Nội", saved.get("name").getAsString());
        check(saved.get("lat").getAsDouble() == 0 && saved.get("lng").getAsDouble() == 0, "Genuine zero coordinates are valid");
        equal("", saved.get("note").getAsString());
        check(!saved.has("userId"), "Location cannot supply an owner");
        JsonObject subscription = data("SUBSCRIBE_WEATHER", "{\"location\":\" Tokyo \",\"condition\":\" Mưa \"}");
        check(!subscription.has("lat") && !subscription.has("lng"), "Name-only subscription must not invent coordinates");
        data("SAVE_LOCATION", "{\"name\":\"Boundary\",\"lat\":-90,\"lng\":-180}");
        data("SUBSCRIBE_WEATHER", "{\"location\":\"Tokyo\",\"condition\":\"Mưa\",\"lat\":90,\"lng\":180}");
        String history = "x".repeat(200);
        JsonObject historyData = new JsonObject();
        historyData.addProperty("keyword", history);
        historyData.addProperty("type", "quoc_gia");
        RequestValidation.validate(new Message("SAVE_HISTORY", historyData));
        for (String type : new String[]{"null", "123", "[]", "\"country\"", "\"\""}) {
            reject("SAVE_HISTORY", "{\"keyword\":\"Tokyo\",\"type\":" + type + "}", "Loại tra cứu");
        }
        reject("SAVE_HISTORY", "{\"keyword\":\"Tokyo\"}", "Loại tra cứu");
        for (String action : new String[]{"GET_PROFILE", "GET_SAVED_LOCATIONS", "GET_HISTORY", "GET_SUBSCRIPTIONS", "LOGOUT"}) {
            for (String payload : new String[]{"null", "{}", "[999]", "999", "{\"userId\":{\"bad\":true}}"}) {
                Message validated = RequestValidation.validate(new Message(action, JsonParser.parseString(payload)));
                equal(action, validated.getAction());
            }
        }
    }

    private static JsonObject data(String action, String json) {
        JsonElement parsed = JsonParser.parseString(json);
        return (JsonObject) RequestValidation.validate(new Message(action, parsed)).getData();
    }

    private static void reject(String action, String json, String label) {
        rejects(() -> data(action, json), label);
    }

    private static void rejects(Runnable task, String label) {
        try { task.run(); throw new AssertionError("Invalid input accepted for " + label); }
        catch (InputValidation.ValidationException expected) {
            check(expected.getMessage().contains(label), "Field-specific error expected for " + label + ": " + expected.getMessage());
            rejected++;
        }
    }

    private static void equal(Object expected, Object actual) {
        check(java.util.Objects.equals(expected, actual), "Expected " + expected + " but got " + actual);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
