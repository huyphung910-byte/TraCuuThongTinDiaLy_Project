package panel;

import model.Country;
import util.VietnameseText;

import java.text.NumberFormat;

/** Nội dung thuần văn bản để dữ liệu từ API không bị diễn giải thành HTML. */
public final class CountryInfoFormatter {
    private CountryInfoFormatter() { }

    public static String format(Country country) {
        if (country == null) return "Không có thông tin quốc gia.";
        StringBuilder output = new StringBuilder();
        String name = VietnameseText.country(country.getTenQuocGia(), country.getMaQuocGia());
        section(output, "1. Tên quốc gia", name + (present(country.getMaQuocGia())
                ? " (" + country.getMaQuocGia().trim() + ")" : ""));
        section(output, "2. Thủ đô", VietnameseText.city(country.getThuDo(), country.getMaQuocGia()));
        StringBuilder geography = new StringBuilder("Châu lục: ")
                .append(VietnameseText.region(country.getRegion()))
                .append("\nTiểu vùng: ").append(VietnameseText.region(country.getSubregion()))
                .append("\nTọa độ quốc gia: ").append(coordinates(country.getLatitude(), country.getLongitude()))
                .append("\nMúi giờ: ").append(value(country.getTimezones()))
                .append("\nQuốc gia giáp biên giới: ").append(Boolean.FALSE.equals(country.getHasLandBorders())
                        ? "Không có biên giới đất liền" : VietnameseText.borders(country.getQuocGiaLienKe()));
        section(output, "3. Vị trí địa lý / Châu lục", geography.toString());
        section(output, "4. Diện tích", finiteNonnegative(country.getArea())
                ? number(country.getArea(), 2) + " km²" : VietnameseText.UNKNOWN);
        section(output, "5. Dân số", country.getPopulation() != null && country.getPopulation() >= 0
                ? NumberFormat.getIntegerInstance(VietnameseText.LOCALE).format(country.getPopulation()) + " người"
                : VietnameseText.UNKNOWN);
        section(output, "6. Ngôn ngữ chính / chính thức", VietnameseText.languages(country.getNgonNgu()));
        section(output, "7. Tiền tệ", VietnameseText.currency(country.getDonViTienTe()));
        section(output, "8. Quốc kỳ", present(country.getUrlQuocKy())
                ? "Xem ảnh và trạng thái tải tại khung quốc kỳ bên trái.\nNguồn ảnh: " + country.getUrlQuocKy().trim()
                : "Chưa có nguồn ảnh quốc kỳ.");
        section(output, "9. Thể chế chính trị", withSource(value(country.getPoliticalSystem()), country.getPoliticalSystemSourceUrl()));
        StringBuilder economy = new StringBuilder("Kinh tế: ").append(value(country.getEconomySummary()));
        if (present(country.getEconomySourceUrl())) economy.append("\nNguồn kinh tế: ").append(country.getEconomySourceUrl().trim());
        economy.append("\nGDP danh nghĩa (USD hiện hành): ");
        if (finiteNonnegative(country.getGdpUsd()) && country.getGdpYear() != null) {
            economy.append(number(country.getGdpUsd(), 0)).append(" USD (năm ").append(country.getGdpYear()).append(")");
        } else {
            economy.append(VietnameseText.UNKNOWN);
        }
        if (present(country.getGdpSourceUrl())) economy.append("\nNguồn GDP: ").append(country.getGdpSourceUrl().trim());
        section(output, "10. Kinh tế / GDP", economy.toString());
        section(output, "11. Khí hậu", withSource(value(country.getClimate()), country.getClimateSourceUrl()));
        String culture = present(country.getCultureAndTourism())
                ? country.getCultureAndTourism().trim() : value(country.getDiemDuLichNoiBat());
        section(output, "12. Văn hóa và du lịch nổi bật", withSource(culture, country.getCultureAndTourismSourceUrl()));
        if (present(country.getMetadataSourceUrl())) output.append("Nguồn thông tin quốc gia: ")
                .append(country.getMetadataSourceUrl().trim()).append('\n');
        if (present(country.getDetailsNotice())) output.append("\nThông báo từ nguồn dữ liệu:\n")
                .append(country.getDetailsNotice().trim()).append('\n');
        if (wikipediaSource(country.getPoliticalSystemSourceUrl()) || wikipediaSource(country.getEconomySourceUrl())
                || wikipediaSource(country.getClimateSourceUrl()) || wikipediaSource(country.getCultureAndTourismSourceUrl())) {
            output.append("\nNội dung từ Wikipedia tiếng Việt được cung cấp theo giấy phép CC BY-SA. ")
                    .append("Xem liên kết nguồn để biết tác giả và lịch sử chỉnh sửa.\n");
        }
        return output.toString();
    }

    private static String coordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return VietnameseText.UNKNOWN;
        return number(latitude, 5) + "° vĩ độ, " + number(longitude, 5) + "° kinh độ";
    }

    private static String number(double value, int maximumFractionDigits) {
        NumberFormat format = NumberFormat.getNumberInstance(VietnameseText.LOCALE);
        format.setMaximumFractionDigits(maximumFractionDigits);
        return format.format(value);
    }

    private static boolean finiteNonnegative(Double number) {
        return number != null && Double.isFinite(number) && number >= 0;
    }

    private static String withSource(String text, String url) {
        return text + (present(url) ? "\nNguồn: " + url.trim() : "");
    }

    private static void section(StringBuilder output, String label, String text) {
        output.append(label).append('\n').append(text).append("\n\n");
    }

    private static String value(String text) { return present(text) ? text.trim() : VietnameseText.UNKNOWN; }
    private static boolean present(String text) { return text != null && !text.isBlank(); }
    private static boolean wikipediaSource(String url) { return present(url) && url.contains("https://vi.wikipedia.org/"); }
}
