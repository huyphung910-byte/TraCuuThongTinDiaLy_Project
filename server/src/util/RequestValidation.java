package util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import model.Message;

import java.util.Locale;

/** Validate request types and values before DAO/API access, on both ends of the socket. */
public final class RequestValidation {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private RequestValidation() { }

    public static Message validate(Message request) {
        if (request == null) throw invalid("Yêu cầu không được để trống.");
        String action = InputValidation.keyword(request.getAction(), "Tên thao tác", 1, 50).toUpperCase(Locale.ROOT);
        // Payloads of reads/logouts are deliberately ignored: ownership comes from the server session.
        switch (action) {
            case "LOGOUT": case "GET_PROFILE": case "GET_SAVED_LOCATIONS":
            case "GET_HISTORY": case "GET_SUBSCRIPTIONS":
                return new Message(action, request.getData());
            default: break;
        }
        // Preserve explicit JSON nulls: supplying null coordinates differs from omitting both fields.
        JsonElement payload = request.getData() instanceof JsonElement
                ? (JsonElement) request.getData() : GSON.toJsonTree(request.getData());
        JsonObject data = new JsonObject();
        switch (action) {
            case "LOGIN": case "REGISTER": {
                JsonObject source = object(payload);
                data.addProperty("username", InputValidation.username(string(source, "username", "Tên đăng nhập")));
                data.addProperty("password", InputValidation.password(string(source, "password", "Mật khẩu"), "Mật khẩu"));
                if (action.equals("REGISTER")) {
                    data.addProperty("fullname", InputValidation.fullname(string(source, "fullname", "Họ tên")));
                    data.addProperty("email", InputValidation.email(string(source, "email", "Email")));
                }
                break;
            }
            case "UPDATE_PROFILE": {
                JsonObject source = object(payload);
                data.addProperty("fullname", InputValidation.fullname(string(source, "fullname", "Họ tên")));
                data.addProperty("email", InputValidation.email(string(source, "email", "Email")));
                break;
            }
            case "CHANGE_PASSWORD": {
                JsonObject source = object(payload);
                String current = InputValidation.password(string(source, "currentPassword", "Mật khẩu hiện tại"), "Mật khẩu hiện tại");
                String next = InputValidation.password(string(source, "newPassword", "Mật khẩu mới"), "Mật khẩu mới");
                if (current.equals(next)) throw invalid("Mật khẩu mới phải khác mật khẩu hiện tại.");
                data.addProperty("currentPassword", current);
                data.addProperty("newPassword", next);
                break;
            }
            case "SEARCH_TRIP_CITIES": case "SEARCH_LOCATION": case "SEARCH_COMPARISON_CITIES":
                keyword(data, payload, "keyword", "Tên thành phố", 2, 200); break;
            case "PLAN_TRIP": {
                JsonObject source = object(payload);
                data.addProperty("id", id(source, "id", "Mã thành phố"));
                int days = id(source, "days", "Số ngày");
                if (days > 7) throw invalid("Số ngày phải từ 1 đến 7.");
                data.addProperty("days", days);
                break;
            }
            case "SEARCH_COUNTRY":
                keyword(data, payload, "countryName", "Tên quốc gia", 1, 200); break;
            case "GET_WEATHER": case "GET_HOTELS":
                keyword(data, payload, "cityName", "Tên thành phố", 2, 200); break;
            case "SAVE_HISTORY": {
                JsonObject source = object(payload);
                keyword(data, source, "keyword", "Từ khóa lịch sử", 1, 255);
                String type = string(source, "type", "Loại tra cứu");
                if (!"thanh_pho".equals(type) && !"quoc_gia".equals(type))
                    throw invalid("Loại tra cứu phải là thanh_pho hoặc quoc_gia.");
                data.addProperty("type", type);
                break;
            }
            case "COMPARE_COUNTRIES": {
                JsonObject source = object(payload);
                keyword(data, source, "leftName", "Quốc gia thứ nhất", 1, 200);
                keyword(data, source, "rightName", "Quốc gia thứ hai", 1, 200);
                break;
            }
            case "UNSUBSCRIBE_WEATHER":
                data.addProperty("id", id(object(payload), "id", "Mã đăng ký")); break;
            case "GET_CITY_DETAILS": case "DELETE_LOCATION":
                data.addProperty("id", id(object(payload), "id", "Mã địa điểm")); break;
            case "COMPARE_CITIES": {
                JsonObject source = object(payload);
                data.addProperty("leftId", id(source, "leftId", "Mã thành phố thứ nhất"));
                data.addProperty("rightId", id(source, "rightId", "Mã thành phố thứ hai"));
                break;
            }
            case "SAVE_LOCATION": {
                JsonObject source = object(payload);
                keyword(data, source, "name", "Tên địa điểm", 1, 255);
                coordinates(data, source, true);
                data.addProperty("note", InputValidation.optionalText(optionalString(source, "note", "Ghi chú"), "Ghi chú", 2000));
                break;
            }
            case "SUBSCRIBE_WEATHER": {
                JsonObject source = object(payload);
                keyword(data, source, "location", "Tên thành phố", 2, 200);
                keyword(data, source, "condition", "Điều kiện cảnh báo", 1, 200);
                coordinates(data, source, false);
                break;
            }
            default: return new Message(action, request.getData());
        }
        return new Message(action, data);
    }

    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw invalid("Dữ liệu yêu cầu phải là một đối tượng JSON.");
        return value.getAsJsonObject();
    }

    private static String string(JsonObject source, String field, String label) {
        JsonElement value = source.get(field);
        if (value == null || value.isJsonNull()) throw invalid(label + " không được để trống.");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid(label + " phải là chuỗi ký tự.");
        return value.getAsString();
    }

    private static String optionalString(JsonObject source, String field, String label) {
        JsonElement value = source.get(field);
        return value == null || value.isJsonNull() ? "" : string(source, field, label);
    }

    private static void keyword(JsonObject data, JsonElement payload, String field, String label, int minimum, int maximum) {
        String value = payload != null && payload.isJsonPrimitive() && payload.getAsJsonPrimitive().isString()
                ? payload.getAsString() : string(object(payload), field, label);
        data.addProperty(field, InputValidation.keyword(value, label, minimum, maximum));
    }

    private static int id(JsonObject data, String field, String label) {
        JsonElement value = number(data, field, label);
        try { return InputValidation.positiveId(value.getAsBigDecimal().intValueExact(), label); }
        catch (ArithmeticException | NumberFormatException e) { throw invalid(label + " phải là số nguyên dương hợp lệ."); }
    }

    private static JsonElement number(JsonObject data, String field, String label) {
        JsonElement value = data.get(field);
        if (value == null || value.isJsonNull()) throw invalid(label + " không được để trống.");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid(label + " phải là số.");
        return value;
    }

    private static void coordinates(JsonObject result, JsonObject source, boolean required) {
        boolean hasLatitude = source.has("lat"), hasLongitude = source.has("lng");
        if (!required && !hasLatitude && !hasLongitude) return;
        double latitude = number(source, "lat", "Vĩ độ").getAsDouble();
        double longitude = number(source, "lng", "Kinh độ").getAsDouble();
        InputValidation.coordinates(latitude, longitude);
        result.addProperty("lat", latitude);
        result.addProperty("lng", longitude);
    }

    private static InputValidation.ValidationException invalid(String message) {
        return new InputValidation.ValidationException(message);
    }
}
