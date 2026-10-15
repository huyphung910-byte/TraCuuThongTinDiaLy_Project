package service;

import com.google.gson.*;
import model.Location;
import java.util.*;

/** Simple daily grouping of real nearby results, adjusted using current weather only. */
public final class TripPlannerService {
    public JsonObject plan(Location city, int days, JsonObject details) {
        if (days < 1 || days > 7) throw new IllegalArgumentException("Số ngày phải từ 1 đến 7.");
        JsonObject result = new JsonObject();
        result.addProperty("city", city.getCityName());
        result.addProperty("latitude", city.getLatitude());
        result.addProperty("longitude", city.getLongitude());
        result.addProperty("radiusMeters", 10000);
        boolean preferIndoor = false;
        if (details.has("weather")) {
            JsonObject weather = details.getAsJsonObject("weather");
            result.add("weather", weather.deepCopy());
            String description = text(weather, "description").toLowerCase(Locale.ROOT);
            preferIndoor = description.matches(".*(rain|storm|snow|mưa|bão|dông|giông|tuyết).*" )
                    || (weather.has("temp") && weather.get("temp").getAsDouble() >= 38);
            result.addProperty("advice", preferIndoor
                    ? "Thời tiết hiện tại không thuận lợi cho đi ngoài trời; ưu tiên điểm có khả năng tham quan trong nhà, kiểm tra điều kiện trước khi đi."
                    : "Có thể cân nhắc các điểm ngoài trời; kiểm tra lại thời tiết trước khi xuất phát.");
        } else {
            result.addProperty("weatherError", text(details, "weatherError"));
            result.addProperty("advice", "Chưa lấy được thời tiết hiện tại; lịch trình chưa được điều chỉnh theo thời tiết.");
        }
        result.addProperty("notice", "Chỉ dùng thời tiết hiện tại, không phải dự báo cho các ngày tiếp theo. Khoảng cách đường thẳng từ tâm thành phố; hãy kiểm tra giờ mở cửa và khả năng tham quan của từng địa điểm.");
        for (String field : new String[]{"placesError", "placesNotice", "placesMetadataError"})
            if (details.has(field)) result.add(field, details.get(field).deepCopy());
        List<JsonObject> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonArray places = details.has("places") ? details.getAsJsonArray("places") : new JsonArray();
        for (JsonElement value : places) {
            if (!value.isJsonObject()) continue;
            JsonObject place = value.getAsJsonObject();
            try {
                double distance = place.get("distance").getAsDouble();
                if (!Double.isFinite(distance) || distance < 0 || distance > 10000 || text(place, "name").isBlank()) continue;
                if (!seen.add(text(place, "name").toLowerCase(Locale.ROOT))) continue;
                JsonObject copy = place.deepCopy();
                String description = (text(place, "name") + " " + text(place, "description")).toLowerCase(Locale.ROOT);
                String kind = description.matches(".*(museum|bảo tàng|gallery|phòng trưng bày|theatre|theater|nhà hát).*" )
                        ? "indoor" : description.matches(".*(park|công viên|garden|vườn|beach|bãi biển|lake|hồ |square|quảng trường).*" ) ? "outdoor" : "unknown";
                copy.addProperty("category", kind);
                candidates.add(copy);
            } catch (RuntimeException ignored) { /* Discard malformed API coordinates/distances. */ }
        }
        final boolean indoor = preferIndoor;
        candidates.sort(Comparator.<JsonObject>comparingInt(p -> {
            String category = text(p, "category");
            return category.equals(indoor ? "indoor" : "outdoor") ? 0 : category.equals("unknown") ? 1 : 2;
        }).thenComparingDouble(p -> p.get("distance").getAsDouble()));
        JsonArray itinerary = new JsonArray();
        int index = 0;
        for (int day = 1; day <= days; day++) {
            JsonObject daily = new JsonObject();
            daily.addProperty("day", day);
            JsonArray stops = new JsonArray();
            int remainingDays = days - day + 1;
            int count = Math.min(3, (int) Math.ceil((candidates.size() - index) / (double) remainingDays));
            for (int stop = 0; stop < count; stop++) stops.add(candidates.get(index++));
            daily.add("places", stops);
            daily.addProperty("note", stops.isEmpty() ? "Chưa có thêm địa điểm phù hợp từ nguồn trong bán kính 10 km; dành thời gian nghỉ hoặc điều chỉnh số ngày."
                    : "Các điểm được ưu tiên theo thời tiết và khoảng cách; phân loại trong/ngoài trời chỉ là gợi ý từ tên và mô tả.");
            itinerary.add(daily);
        }
        result.add("days", itinerary);
        return result;
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
