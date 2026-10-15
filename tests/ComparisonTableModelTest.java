package panel;

import com.google.gson.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;

/** Client-only comparison checks: no sockets, HTTP requests, or database access. */
public final class ComparisonTableModelTest {
    private static final String UNKNOWN = "Chưa có dữ liệu";
    private static final String DASH = "—";

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        SwingUtilities.invokeAndWait(() -> {
            checkCityComparison();
            checkCountryComparison();
            checkMissingData();
            checkZeroData();
            checkWeatherErrors();
            checkBorders();
        });
        if (args.length > 0 && "--preview".equals(args[0])) {
            SwingUtilities.invokeAndWait(ComparisonTableModelTest::writePreview);
        }
        System.out.println("ĐẠT: bảng so sánh thành phố/quốc gia, chênh lệch số, điểm phần trăm độ ẩm, dữ liệu thiếu và bằng không, lỗi thời tiết rõ ràng, trạng thái biên giới và ô chỉ đọc");
    }

    private static JsonObject cityFixture() {
        return JsonParser.parseString("""
                {"left":{
                  "city":{"cityName":"Hà Nội","country":"Vietnam","description":"Hanoi",
                    "population":1000000,"latitude":21.03,"longitude":105.85,"timezone":"Asia/Ho_Chi_Minh"},
                  "country":{"thuDo":"Hanoi","donViTienTe":"Vietnamese dong (VND)","ngonNgu":"Vietnamese"},
                  "weather":{"temp":30.5,"humidity":80,"windSpeed":3.2,"description":"clear sky",
                    "updatedAt":"2026-10-06T10:00:00Z","fromCache":true}},
                 "right":{
                  "city":{"cityName":"Tokyo","country":"Japan","description":"Tokyo",
                    "population":12000000,"latitude":35.68,"longitude":139.69,"timezone":"Asia/Tokyo"},
                  "country":{"thuDo":"Tokyo","donViTienTe":"Japanese yen (JPY)","ngonNgu":"Japanese"},
                  "weather":{"temp":18.5,"humidity":65,"windSpeed":4.7,"description":"overcast clouds",
                    "updatedAt":"2026-10-06T10:00:00Z","fromCache":false}}}
                """).getAsJsonObject();
    }

    private static void checkCityComparison() {
        ComparisonTableModel model = new ComparisonTableModel();
        model.display(cityFixture(), true);
        check(model.getColumnName(1).equals("Hà Nội") && model.getColumnName(2).equals("Tokyo"), "City column names");
        cell(model, "Quốc gia", 1, "Việt Nam");
        cell(model, "Dân số (nguồn API)", 3, "-11.000.000 người");
        cell(model, "Nhiệt độ", 3, "12,0 °C");
        cell(model, "Độ ẩm", 1, "80 %");
        cell(model, "Độ ẩm", 3, "15 điểm %");
        cell(model, "Gió", 3, "-1,5 m/s");
        cell(model, "Tiền tệ của quốc gia", 1, java.util.Currency.getInstance("VND").getDisplayName(util.VietnameseText.LOCALE) + " (VND)");
        cell(model, "Ngôn ngữ của quốc gia", 2, "Tiếng Nhật");
        cell(model, "Mô tả thời tiết", 2, "Mây phủ kín");
        cell(model, "Múi giờ thành phố", 1, "Asia/Ho_Chi_Minh");
        cell(model, "Nguồn thời tiết", 1, "Bộ nhớ đệm còn hiệu lực");
        cell(model, "Nguồn thời tiết", 2, "OpenWeather");
        cell(model, "Lỗi lấy thời tiết", 1, DASH);
        cell(model, "Lỗi lấy thời tiết", 2, DASH);
        cell(model, "Thời điểm dữ liệu (UTC)", 1, "06/10/2026 10:00:00");
        check(findRow(model, "Diện tích quốc gia") < 0, "Country area must not be represented as city area");
        for (int row = 0; row < model.getRowCount(); row++) {
            for (int col = 0; col < model.getColumnCount(); col++) {
                check(!model.isCellEditable(row, col), "All comparison cells must be read-only");
            }
        }
    }

    private static void checkCountryComparison() {
        JsonObject fixture = cityFixture();
        JsonObject left = fixture.getAsJsonObject("left");
        JsonObject right = fixture.getAsJsonObject("right");
        populateCountry(left.getAsJsonObject("country"), "Vietnam", "VN", 100000000L, 330000, 16, 108);
        populateCountry(right.getAsJsonObject("country"), "Japan", "JP", 125000000L, 378000, 36, 138);
        left.addProperty("weatherLocation", "Hanoi, VN");
        right.addProperty("weatherLocation", "Tokyo, JP");
        ComparisonTableModel model = new ComparisonTableModel();
        model.display(cityFixture(), true);
        int cityRows = model.getRowCount();
        model.display(fixture, false);
        check(model.getColumnName(1).equals("Việt Nam") && model.getColumnName(2).equals("Nhật Bản"), "Country column names");
        cell(model, "Mã quốc gia", 2, "JP");
        cell(model, "Dân số (nguồn API)", 3, "-25.000.000 người");
        cell(model, "Diện tích quốc gia", 3, "-48.000,0 km²");
        cell(model, "Vĩ độ quốc gia", 1, "16,00000°");
        cell(model, "Kinh độ quốc gia", 2, "138,00000°");
        cell(model, "Địa điểm lấy thời tiết", 1, "Hà Nội, VN");
        cell(model, "Nhiệt độ tại thủ đô", 3, "12,0 °C");
        cell(model, "Độ ẩm tại thủ đô", 3, "15 điểm %");
        cell(model, "Gió tại thủ đô", 3, "-1,5 m/s");
        cell(model, "Khu vực", 1, "Châu Á");
        cell(model, "Tiểu vùng", 1, "Test subregion");
        cell(model, "Múi giờ quốc gia", 1, "UTC+07:00");
        cell(model, "Các nước giáp biên giới", 1, "XX, YY");
        check(findRow(model, "Tỉnh / vùng") < 0 && findRow(model, "Múi giờ thành phố") < 0,
                "Changing mode must clear old rows");
        check(model.getRowCount() < cityRows + 10, "New comparison must replace previous rows");
    }

    private static void populateCountry(JsonObject country, String name, String code, long population,
                                        double area, double latitude, double longitude) {
        country.addProperty("tenQuocGia", name);
        country.addProperty("maQuocGia", code);
        country.addProperty("population", population);
        country.addProperty("area", area);
        country.addProperty("latitude", latitude);
        country.addProperty("longitude", longitude);
        country.addProperty("region", "Asia");
        country.addProperty("subregion", "Test subregion");
        country.addProperty("timezones", "UTC+07:00");
        country.addProperty("quocGiaLienKe", "XX, YY");
        country.addProperty("hasLandBorders", true);
    }

    private static void checkMissingData() {
        ComparisonTableModel model = new ComparisonTableModel();
        JsonObject fixture = cityFixture();
        JsonObject left = fixture.getAsJsonObject("left");
        left.remove("weather");
        left.remove("country");
        left.addProperty("weatherError", "OpenWeather: HTTP 401");
        JsonObject city = left.getAsJsonObject("city");
        city.remove("population");
        city.add("latitude", JsonNull.INSTANCE);
        city.addProperty("longitude", "Infinity");
        model.display(fixture, true);
        for (String label : new String[]{"Nhiệt độ", "Độ ẩm", "Gió", "Dân số (nguồn API)", "Vĩ độ thành phố", "Kinh độ thành phố"}) {
            cell(model, label, 1, UNKNOWN);
            cell(model, label, 3, DASH);
        }
        cell(model, "Nguồn thời tiết", 1, "Không tải được thời tiết");
        cell(model, "Lỗi lấy thời tiết", 1, "OpenWeather: HTTP 401");
        cell(model, "Tiền tệ của quốc gia", 1, UNKNOWN);
        cell(model, "Thủ đô của quốc gia", 1, UNKNOWN);
        cell(model, "Ngôn ngữ của quốc gia", 1, UNKNOWN);
        model.display(new JsonObject(), false);
        cell(model, "Dân số (nguồn API)", 1, UNKNOWN);
        cell(model, "Diện tích quốc gia", 2, UNKNOWN);
        cell(model, "Nhiệt độ tại thủ đô", 3, DASH);
        cell(model, "Nguồn thời tiết", 1, UNKNOWN);
        cell(model, "Lỗi lấy thời tiết", 1, DASH);
    }

    private static void checkZeroData() {
        ComparisonTableModel model = new ComparisonTableModel();
        JsonObject fixture = cityFixture();
        JsonObject leftWeather = fixture.getAsJsonObject("left").getAsJsonObject("weather");
        leftWeather.addProperty("temp", 0);
        leftWeather.addProperty("humidity", 0);
        leftWeather.addProperty("windSpeed", 0);
        model.display(fixture, true);
        cell(model, "Nhiệt độ", 1, "0,0 °C");
        cell(model, "Nhiệt độ", 3, "-18,5 °C");
        cell(model, "Độ ẩm", 1, "0 %");
        cell(model, "Độ ẩm", 3, "-65 điểm %");
        cell(model, "Gió", 1, "0,0 m/s");
    }

    private static void checkWeatherErrors() {
        ComparisonTableModel model = new ComparisonTableModel();
        JsonObject fixture = cityFixture();
        JsonObject left = fixture.getAsJsonObject("left"), right = fixture.getAsJsonObject("right");
        left.remove("weather");
        left.addProperty("weatherError", "Thiếu API key OpenWeather.");
        right.addProperty("countryError", "Country API is unavailable");
        model.display(fixture, true);
        cell(model, "Nguồn thời tiết", 1, "Không tải được thời tiết");
        cell(model, "Lỗi lấy thời tiết", 1, "Thiếu API key OpenWeather.");
        cell(model, "Nhiệt độ", 1, UNKNOWN);
        cell(model, "Nhiệt độ", 3, DASH);
        cell(model, "Nguồn thời tiết", 2, "OpenWeather");
        cell(model, "Lỗi lấy thời tiết", 2, DASH);
        // Failed country lookup means no coordinates could be obtained for capital weather.
        left.remove("country");
        left.remove("weatherError");
        left.addProperty("error", "Country API: timeout");
        model.display(fixture, false);
        cell(model, "Lỗi lấy thời tiết", 1, "Country API: timeout");
        cell(model, "Nguồn thời tiết", 1, "Không tải được thời tiết");
        cell(model, "Nhiệt độ tại thủ đô", 1, UNKNOWN);
    }

    private static void checkBorders() {
        ComparisonTableModel model = new ComparisonTableModel();
        JsonObject fixture = cityFixture();
        JsonObject left = fixture.getAsJsonObject("left").getAsJsonObject("country");
        JsonObject right = fixture.getAsJsonObject("right").getAsJsonObject("country");
        left.addProperty("hasLandBorders", true);
        left.addProperty("quocGiaLienKe", "CHN, LAO, KHM");
        right.addProperty("hasLandBorders", false);
        right.addProperty("quocGiaLienKe", "");
        model.display(fixture, false);
        cell(model, "Các nước giáp biên giới", 1, "Trung Quốc, Lào, Campuchia");
        cell(model, "Các nước giáp biên giới", 2, "Không có biên giới đất liền");
        left.add("hasLandBorders", JsonNull.INSTANCE);
        right.remove("hasLandBorders");
        right.addProperty("quocGiaLienKe", "");
        model.display(fixture, false);
        cell(model, "Các nước giáp biên giới", 1, "Trung Quốc, Lào, Campuchia");
        cell(model, "Các nước giáp biên giới", 2, UNKNOWN);
        left.remove("hasLandBorders");
        model.display(fixture, false);
        cell(model, "Các nước giáp biên giới", 1, "Trung Quốc, Lào, Campuchia");
        cell(model, "Các nước giáp biên giới", 2, UNKNOWN);
        left.addProperty("hasLandBorders", true);
        left.addProperty("quocGiaLienKe", " ");
        right.addProperty("hasLandBorders", "false");
        model.display(fixture, false);
        cell(model, "Các nước giáp biên giới", 1, UNKNOWN);
        cell(model, "Các nước giáp biên giới", 2, UNKNOWN);
    }

    private static void writePreview() {
        try {
            ComparisonPanel panel = new ComparisonPanel(null);
            Field field = ComparisonPanel.class.getDeclaredField("model");
            field.setAccessible(true);
            ((ComparisonTableModel)field.get(panel)).display(cityFixture(), true);
            panel.setSize(980, 580);
            // JTable configures its enclosing scroll pane's header on addNotify.
            panel.addNotify();
            layout(panel);
            BufferedImage image = new BufferedImage(980, 580, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try { panel.printAll(graphics); } finally { graphics.dispose(); panel.removeNotify(); }
            File preview = new File("build/comparison-preview.png");
            File directory = preview.getParentFile();
            if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException("Cannot create preview directory");
            ImageIO.write(image, "png", preview);
            System.out.println("Ảnh xem trước: " + preview.getAbsolutePath());
        } catch (Exception e) { throw new AssertionError("Headless comparison preview failed", e); }
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container) layout((Container)child);
    }

    private static int findRow(ComparisonTableModel model, String label) {
        for (int i = 0; i < model.getRowCount(); i++) if (label.equals(model.getValueAt(i, 0))) return i;
        return -1;
    }

    private static void cell(ComparisonTableModel model, String label, int col, String expected) {
        int row = findRow(model, label);
        check(row >= 0, "Missing row: " + label);
        check(expected.equals(model.getValueAt(row, col)), label + " column " + col + ": expected " + expected
                + ", got " + model.getValueAt(row, col));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
