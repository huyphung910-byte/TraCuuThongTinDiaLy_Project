import com.formdev.flatlaf.FlatLightLaf;
import util.VietnameseText;
import javax.swing.*;
import java.util.Locale;

/** Kiểm tra nội dung tiếng Việt độc lập với mạng và cơ sở dữ liệu. */
public final class VietnameseTextTest {
    public static void main(String[] args) throws Exception {
        // Không phụ thuộc ngôn ngữ của máy đang chạy ứng dụng.
        Locale.setDefault(Locale.ENGLISH);
        equal(VietnameseText.country("Japan", "JP"), "Nhật Bản");
        equal(VietnameseText.country("China", "CHN"), "Trung Quốc");
        equal(VietnameseText.country("Vietnam"), "Việt Nam");
        equal(VietnameseText.country("United States of America"), "Hoa Kỳ");
        equal(VietnameseText.country("Địa điểm thử nghiệm", "ZZ"), "Địa điểm thử nghiệm");
        equal(VietnameseText.borders("CHN, LAO, KHM"), "Trung Quốc, Lào, Campuchia");
        equal(VietnameseText.city("Hanoi"), "Hà Nội");
        equal(VietnameseText.city("Beijing"), "Bắc Kinh");
        equal(VietnameseText.city("Washington, D.C."), "Washington, D.C.");
        equal(VietnameseText.city("Dalat", "VN"), "Đà Lạt");
        equal(VietnameseText.city("Dalat", "MY"), "Dalat");
        equal(VietnameseText.city("Dalat"), "Dalat");
        equal(VietnameseText.city("Hanoi", "US"), "Hanoi");
        equal(VietnameseText.location("Hanoi, Vietnam"), "Hà Nội, Việt Nam");
        equal(VietnameseText.location("Washington, D.C., United States of America"), "Washington, D.C., Hoa Kỳ");
        equal(VietnameseText.region("Asia"), "Châu Á");
        equal(VietnameseText.region("South-Eastern Asia"), "Đông Nam Á");
        equal(VietnameseText.region("Eastern Asia"), "Đông Á");
        equal(VietnameseText.languages("Vietnamese, Japanese, English"), "Tiếng Việt, Tiếng Nhật, Tiếng Anh");
        equal(VietnameseText.weather("overcast clouds"), "Mây phủ kín");
        equal(VietnameseText.weather("light rain"), "Mưa nhẹ");
        equal(VietnameseText.weather("Mưa nhẹ"), "Mưa nhẹ");
        equal(VietnameseText.dateTime("2026-10-06T10:00:00Z"), "06/10/2026 10:00:00");
        equal(VietnameseText.dateTime("2026-10-06 10:00:00.0"), "06/10/2026 10:00:00");
        equal(VietnameseText.dateTime("Không có thời gian"), "Không có thời gian");
        equal(VietnameseText.errorMessage(new java.util.concurrent.ExecutionException(
                new java.io.IOException("Kết nối đã đóng. Hãy đăng nhập lại.", new java.net.SocketException("Socket closed")))),
                "Kết nối đã đóng. Hãy đăng nhập lại.");
        equal(VietnameseText.errorMessage(new IllegalStateException("Unexpected failure")),
                "Không hoàn thành được yêu cầu. Hãy thử lại.");
        for (String raw : new String[]{null, "", "  "}) {
            equal(VietnameseText.country(raw), VietnameseText.UNKNOWN);
            equal(VietnameseText.weather(raw), VietnameseText.UNKNOWN);
            equal(VietnameseText.currency(raw), VietnameseText.UNKNOWN);
        }
        String currency = VietnameseText.currency("Japanese yen (JPY, ¥), United States dollar (USD, $)");
        check(currency.contains("(JPY, ¥)") && currency.contains("(USD, $)")
                && !currency.contains("Japanese") && !currency.contains("United States"), "Việt hóa tên và giữ mã/ký hiệu tiền tệ");
        check(VietnameseText.currency("VND").endsWith("(VND)"), "Mã tiền tệ đơn lẻ");
        String mixed = VietnameseText.currency("Unlisted credits, Japanese yen (JPY, ¥), Other credits");
        check(mixed.startsWith("Unlisted credits, ") && mixed.endsWith(", Other credits")
                && mixed.contains("(JPY, ¥)"), "Giữ các tiền tệ chưa có bản dịch trong danh sách");
        equal(VietnameseText.currency("Fixture currency (XXXINVALID, ?)") , "Fixture currency (XXXINVALID, ?)");
        SwingUtilities.invokeAndWait(() -> {
            FlatLightLaf.setup();
            ClientMain.configureVietnameseUI();
            equal(UIManager.getString("OptionPane.yesButtonText"), "Có");
            equal(UIManager.getString("OptionPane.noButtonText"), "Không");
            equal(UIManager.getString("OptionPane.cancelButtonText"), "Hủy");
            equal(UIManager.getString("OptionPane.okButtonText"), "Đồng ý");
            equal(UIManager.getString("FileChooser.openButtonText"), "Mở");
            equal(UIManager.getString("FileChooser.saveButtonText"), "Lưu");
            equal(JComponent.getDefaultLocale().toLanguageTag(), "vi-VN");
        });
        System.out.println("ĐẠT: tên quốc gia, thành phố, tiền tệ, ngôn ngữ, khu vực, thời tiết, ngày giờ và nút hộp thoại tiếng Việt.");
    }
    private static void equal(String actual, String expected) {
        check(expected.equals(actual), "Mong đợi '" + expected + "', nhận '" + actual + "'");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
