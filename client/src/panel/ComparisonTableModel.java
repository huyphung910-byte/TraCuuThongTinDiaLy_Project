package panel;

import com.google.gson.*;
import javax.swing.table.DefaultTableModel;
import java.util.Locale;
import util.VietnameseText;

/** Builds a comparison from API data; missing values are never treated as zero. */
public final class ComparisonTableModel extends DefaultTableModel {
    private static final String UNKNOWN = "Chưa có dữ liệu";
    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    public ComparisonTableModel() {
        super(new String[]{"Tiêu chí", "Bên trái", "Bên phải", "Chênh lệch (trái − phải)"}, 0);
    }

    @Override public boolean isCellEditable(int row, int column) { return false; }

    public void display(JsonObject result, boolean cities) {
        setRowCount(0);
        JsonObject left = object(result, "left"), right = object(result, "right");
        JsonObject l = object(left, cities ? "city" : "country"), r = object(right, cities ? "city" : "country");
        setColumnIdentifiers(new String[]{"Tiêu chí", placeName(l, cities),
                placeName(r, cities), "Chênh lệch (trái − phải)"});
        JsonObject lc = object(left, "country"), rc = object(right, "country");
        if (cities) {
            strings("Quốc gia", l, r, "country");
            strings("Tỉnh / vùng", l, r, "description");
        } else strings("Mã quốc gia", l, r, "maQuocGia");
        numbers("Dân số (nguồn API)", l, r, "population", " người", 0);
        numbers(cities ? "Vĩ độ thành phố" : "Vĩ độ quốc gia", l, r, "latitude", "°", 5);
        numbers(cities ? "Kinh độ thành phố" : "Kinh độ quốc gia", l, r, "longitude", "°", 5);
        strings(cities ? "Thủ đô của quốc gia" : "Thủ đô", lc, rc, "thuDo");
        strings(cities ? "Tiền tệ của quốc gia" : "Tiền tệ", lc, rc, "donViTienTe");
        strings(cities ? "Ngôn ngữ của quốc gia" : "Ngôn ngữ", lc, rc, "ngonNgu");
        if (!cities) {
            numbers("Diện tích quốc gia", lc, rc, "area", " km²", 1);
            strings("Khu vực", lc, rc, "region");
            strings("Tiểu vùng", lc, rc, "subregion");
            strings("Múi giờ quốc gia", lc, rc, "timezones");
            addRow(new Object[]{"Các nước giáp biên giới", borders(lc), borders(rc), "—"});
        } else strings("Múi giờ thành phố", l, r, "timezone");
        addRow(new Object[]{"Địa điểm lấy thời tiết", weatherLocation(left, l, cities), weatherLocation(right, r, cities), "—"});
        JsonObject lw = object(left, "weather"), rw = object(right, "weather");
        String suffix = cities ? "" : " tại thủ đô";
        numbers("Nhiệt độ" + suffix, lw, rw, "temp", " °C", 1);
        numbers("Độ ẩm" + suffix, lw, rw, "humidity", " %", 0);
        numbers("Gió" + suffix, lw, rw, "windSpeed", " m/s", 1);
        strings("Mô tả thời tiết", lw, rw, "description");
        strings("Thời điểm dữ liệu (UTC)", lw, rw, "updatedAt");
        addRow(new Object[]{"Nguồn thời tiết", weatherSource(left, lw), weatherSource(right, rw), "—"});
        addRow(new Object[]{"Lỗi lấy thời tiết", weatherError(left, lw), weatherError(right, rw), "—"});
    }

    private void strings(String label, JsonObject left, JsonObject right, String field) {
        addRow(new Object[]{label, displayText(left, field, label), displayText(right, field, label), "—"});
    }

    private static String placeName(JsonObject place, boolean city) {
        return city ? VietnameseText.city(text(place, "cityName"), text(place, "countryCode"))
                : VietnameseText.country(text(place, "tenQuocGia"), text(place, "maQuocGia"));
    }

    private static String displayText(JsonObject json, String field, String label) {
        String value = text(json, field);
        switch (field) {
            case "country": return VietnameseText.country(value, text(json, "countryCode"));
            case "thuDo": return VietnameseText.city(value, text(json, "maQuocGia"));
            case "donViTienTe": return VietnameseText.currency(value);
            case "ngonNgu": return VietnameseText.languages(value);
            case "region": case "subregion": return VietnameseText.region(value);
            case "updatedAt": return VietnameseText.dateTime(value);
            case "description": return label.equals("Mô tả thời tiết")
                    ? VietnameseText.weather(value) : VietnameseText.city(value, text(json, "countryCode"));
            default: return value;
        }
    }

    private void numbers(String label, JsonObject left, JsonObject right, String field, String unit, int decimals) {
        Double l = number(left, field), r = number(right, field);
        addRow(new Object[]{label, format(l, unit, decimals), format(r, unit, decimals),
                l == null || r == null ? "—" : format(l - r, unit.equals(" %") ? " điểm %" : unit, decimals)});
    }

    private static String format(Double value, String unit, int decimals) {
        return value == null ? UNKNOWN : String.format(VI, "%,." + decimals + "f", value) + unit;
    }

    private static Double number(JsonObject json, String field) {
        try {
            double value = json.get(field).getAsDouble();
            return Double.isFinite(value) ? value : null;
        } catch (RuntimeException e) { return null; }
    }

    public static JsonObject object(JsonObject json, String field) {
        return json != null && json.has(field) && json.get(field).isJsonObject()
                ? json.getAsJsonObject(field) : new JsonObject();
    }

    public static String text(JsonObject json, String field) {
        return json != null && json.has(field) && json.get(field).isJsonPrimitive()
                && !json.get(field).getAsString().isBlank() ? json.get(field).getAsString() : UNKNOWN;
    }

    private static String weatherLocation(JsonObject side, JsonObject place, boolean cities) {
        String code = text(place, cities ? "countryCode" : "maQuocGia");
        if (side.has("weatherLocation")) return VietnameseText.city(text(side, "weatherLocation"), code);
        return VietnameseText.city(text(place, cities ? "cityName" : "thuDo"), code);
    }

    private static String borders(JsonObject country) {
        JsonElement value = country.get("hasLandBorders");
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                && !value.getAsBoolean()) return "Không có biên giới đất liền";
        return VietnameseText.borders(text(country, "quocGiaLienKe"));
    }

    private static String weatherSource(JsonObject side, JsonObject weather) {
        if (weather.isEmpty()) return "—".equals(weatherError(side, weather)) ? UNKNOWN : "Không tải được thời tiết";
        return weather.has("fromCache") && weather.get("fromCache").isJsonPrimitive()
                && weather.get("fromCache").getAsBoolean()
                ? "Bộ nhớ đệm còn hiệu lực" : "OpenWeather";
    }

    private static String weatherError(JsonObject side, JsonObject weather) {
        if (!weather.isEmpty()) return "—";
        for (String field : new String[]{"weatherError", "error"}) {
            String error = text(side, field);
            if (!UNKNOWN.equals(error)) return error;
        }
        return "—";
    }
}
