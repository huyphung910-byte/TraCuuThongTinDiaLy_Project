import com.google.gson.*;
import model.Location;
import service.TripPlannerService;
import util.RequestValidation;
import model.Message;
import java.util.HashSet;

public final class TripPlannerServiceTest {
    public static void main(String[] args) {
        Location city = new Location(1, "Hà Nội", "Việt Nam", 21.0285, 105.8542, "");
        JsonObject details = JsonParser.parseString("""
                {"weather":{"description":"mưa lớn","temp":25,"updatedAt":"2026-10-06T00:00:00Z"},
                 "places":[
                    {"name":"Công viên A","distance":100,"url":"https://vi.wikipedia.org/?curid=1"},
                    {"name":"Bảo tàng B","distance":2000},
                    {"name":"Quảng trường C","distance":10000},
                    {"name":"Ngoài phạm vi","distance":10000.1},
                    {"name":"Bảo tàng B","distance":2001},
                    {"name":"Invalid","distance":-1}]}
                """).getAsJsonObject();
        TripPlannerService planner = new TripPlannerService();
        JsonObject rainy = planner.plan(city, 2, details);
        check(first(rainy).equals("Bảo tàng B"), "Rain favors indoor candidates over nearest park");
        check(rainy.getAsJsonArray("days").size() == 2, "Requested days returned");
        HashSet<String> names = new HashSet<>();
        for (JsonElement value : rainy.getAsJsonArray("days")) {
            JsonArray stops = value.getAsJsonObject().getAsJsonArray("places");
            check(stops.size() <= 3, "At most three daily stops");
            for (JsonElement stop : stops) {
                JsonObject place = stop.getAsJsonObject();
                check(names.add(place.get("name").getAsString()), "No repeated stops");
                check(place.get("distance").getAsDouble() <= 10000, "Within radius");
            }
        }
        check(names.size() == 3 && names.contains("Quảng trường C"), "Boundary included, invalid and duplicate excluded");
        details.getAsJsonObject("weather").addProperty("description", "trời quang");
        check(first(planner.plan(city, 1, details)).equals("Công viên A"), "Clear weather favors outside");
        details.getAsJsonObject("weather").addProperty("temp", 39);
        check(first(planner.plan(city, 1, details)).equals("Bảo tàng B"), "Hot weather favors indoor candidates");
        details.remove("weather"); details.addProperty("weatherError", "Thiếu khóa API");
        check(planner.plan(city, 7, details).has("weatherError"), "Weather failure remains explicit");
        details.add("places", new JsonArray()); details.addProperty("placesError", "Nguồn không khả dụng");
        JsonObject empty = planner.plan(city, 7, details);
        check(empty.getAsJsonArray("days").size() == 7 && empty.has("placesError"), "No invented places on source failure");
        for (String days : new String[]{"0", "8", "-1", "1.5", "\"2\"", "null"}) {
            try {
                RequestValidation.validate(new Message("PLAN_TRIP", JsonParser.parseString("{\"id\":1,\"days\":" + days + "}")));
                throw new AssertionError("Invalid day count accepted: " + days);
            } catch (util.InputValidation.ValidationException expected) { }
        }
        System.out.println("TripPlannerServiceTest: weather ranking, 10km boundary, daily grouping, failure handling and input checks passed.");
    }
    private static String first(JsonObject plan) {
        return plan.getAsJsonArray("days").get(0).getAsJsonObject().getAsJsonArray("places")
                .get(0).getAsJsonObject().get("name").getAsString();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
