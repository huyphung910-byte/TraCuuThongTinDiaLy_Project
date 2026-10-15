package util;

import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Việt hóa dữ liệu khi hiển thị, giữ nguyên các giá trị dùng để tra cứu và lưu trữ. */
public final class VietnameseText {
    public static final Locale LOCALE = Locale.forLanguageTag("vi-VN");
    public static final String UNKNOWN = "Chưa có dữ liệu";
    private static final Map<String, String> COUNTRY_CODES = new HashMap<>();
    private static final Map<String, String> COUNTRY_NAMES = new HashMap<>();
    private static final Map<String, String> LANGUAGE_NAMES = new HashMap<>();
    private static final Map<String, Currency> CURRENCY_NAMES = new HashMap<>();
    private static final Map<String, String> CITY_COUNTRIES = new HashMap<>();
    private static final Pattern CURRENCY_CODE = Pattern.compile("\\(([A-Z]{3})(?:,[^)]*)?\\)");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", LOCALE);
    private static final Map<String, String> CITIES = translations("""
            Hanoi|Hà Nội
            Ha Noi|Hà Nội
            Ho Chi Minh|Thành phố Hồ Chí Minh
            Ho Chi Minh City|Thành phố Hồ Chí Minh
            Saigon|Thành phố Hồ Chí Minh
            Da Nang|Đà Nẵng
            Haiphong|Hải Phòng
            Hai Phong|Hải Phòng
            Hue|Huế
            Can Tho|Cần Thơ
            Nha Trang|Nha Trang
            Da Lat|Đà Lạt
            Dalat|Đà Lạt
            Vung Tau|Vũng Tàu
            Quy Nhon|Quy Nhơn
            Hoi An|Hội An
            Ha Long|Hạ Long
            Beijing|Bắc Kinh
            Peking|Bắc Kinh
            Shanghai|Thượng Hải
            Guangzhou|Quảng Châu
            Shenzhen|Thâm Quyến
            Chongqing|Trùng Khánh
            Tianjin|Thiên Tân
            Nanjing|Nam Kinh
            Wuhan|Vũ Hán
            Hangzhou|Hàng Châu
            Harbin|Cáp Nhĩ Tân
            Xi'an|Tây An
            Xian|Tây An
            Hong Kong|Hồng Kông
            Macau|Ma Cao
            Macao|Ma Cao
            Taipei|Đài Bắc
            Kaohsiung|Cao Hùng
            Pyongyang|Bình Nhưỡng
            Phnom Penh|Phnom Penh
            New Delhi|New Delhi
            """);
    private static final Map<String, String> REGIONS = translations("""
            Africa|Châu Phi
            Americas|Châu Mỹ
            America|Châu Mỹ
            Asia|Châu Á
            Europe|Châu Âu
            Oceania|Châu Đại Dương
            Antarctic|Châu Nam Cực
            Antarctica|Châu Nam Cực
            North America|Bắc Mỹ
            Northern America|Bắc Mỹ
            Central America|Trung Mỹ
            South America|Nam Mỹ
            Caribbean|Vùng Caribe
            Western Africa|Tây Phi
            Eastern Africa|Đông Phi
            Northern Africa|Bắc Phi
            Southern Africa|Nam Phi
            Middle Africa|Trung Phi
            Central Africa|Trung Phi
            Western Asia|Tây Á
            Eastern Asia|Đông Á
            Southern Asia|Nam Á
            South-Eastern Asia|Đông Nam Á
            Southeast Asia|Đông Nam Á
            Central Asia|Trung Á
            Western Europe|Tây Âu
            Eastern Europe|Đông Âu
            Northern Europe|Bắc Âu
            Southern Europe|Nam Âu
            Australia and New Zealand|Úc và New Zealand
            Melanesia|Vùng Melanesia
            Micronesia|Vùng Micronesia
            Polynesia|Vùng Polynesia
            """);
    private static final Map<String, String> WEATHER = translations("""
            clear sky|Trời quang
            few clouds|Ít mây
            scattered clouds|Mây rải rác
            broken clouds|Nhiều mây
            overcast clouds|Mây phủ kín
            clouds|Có mây
            thunderstorm with light rain|Dông kèm mưa nhẹ
            thunderstorm with rain|Dông kèm mưa
            thunderstorm with heavy rain|Dông kèm mưa lớn
            light thunderstorm|Dông nhẹ
            thunderstorm|Dông
            heavy thunderstorm|Dông mạnh
            ragged thunderstorm|Dông rải rác
            thunderstorm with light drizzle|Dông kèm mưa phùn nhẹ
            thunderstorm with drizzle|Dông kèm mưa phùn
            thunderstorm with heavy drizzle|Dông kèm mưa phùn dày
            light intensity drizzle|Mưa phùn nhẹ
            drizzle|Mưa phùn
            heavy intensity drizzle|Mưa phùn dày
            light intensity drizzle rain|Mưa nhẹ kèm mưa phùn
            drizzle rain|Mưa kèm mưa phùn
            heavy intensity drizzle rain|Mưa lớn kèm mưa phùn
            shower rain and drizzle|Mưa rào kèm mưa phùn
            heavy shower rain and drizzle|Mưa rào lớn kèm mưa phùn
            shower drizzle|Mưa phùn rải rác
            light rain|Mưa nhẹ
            moderate rain|Mưa vừa
            heavy intensity rain|Mưa lớn
            very heavy rain|Mưa rất lớn
            extreme rain|Mưa cực lớn
            freezing rain|Mưa đóng băng
            light intensity shower rain|Mưa rào nhẹ
            shower rain|Mưa rào
            heavy intensity shower rain|Mưa rào lớn
            ragged shower rain|Mưa rào rải rác
            rain|Có mưa
            light snow|Tuyết nhẹ
            snow|Tuyết rơi
            heavy snow|Tuyết rơi dày
            sleet|Mưa tuyết
            light shower sleet|Mưa tuyết nhẹ
            shower sleet|Mưa tuyết rải rác
            light rain and snow|Mưa nhẹ kèm tuyết
            rain and snow|Mưa kèm tuyết
            light shower snow|Tuyết rào nhẹ
            shower snow|Tuyết rào
            heavy shower snow|Tuyết rào dày
            mist|Sương mỏng
            smoke|Khói
            haze|Mù nhẹ
            sand/dust whirls|Lốc cát hoặc bụi
            fog|Sương mù
            sand|Cát trong không khí
            dust|Bụi trong không khí
            volcanic ash|Tro núi lửa
            squalls|Gió giật mạnh
            tornado|Lốc xoáy
            """);

    static {
        cityCountries("VN", "Hanoi|Ha Noi|Ho Chi Minh|Ho Chi Minh City|Saigon|Da Nang|Haiphong|Hai Phong|Hue|Can Tho|Nha Trang|Da Lat|Dalat|Vung Tau|Quy Nhon|Hoi An|Ha Long");
        cityCountries("CN", "Beijing|Peking|Shanghai|Guangzhou|Shenzhen|Chongqing|Tianjin|Nanjing|Wuhan|Hangzhou|Harbin|Xi'an|Xian");
        cityCountries("HK", "Hong Kong");
        cityCountries("MO", "Macau|Macao");
        cityCountries("TW", "Taipei|Kaohsiung");
        cityCountries("KP", "Pyongyang");
        for (String code : Locale.getISOCountries()) {
            Locale country = new Locale("", code);
            COUNTRY_CODES.put(code, code);
            try { COUNTRY_CODES.put(country.getISO3Country(), code); }
            catch (MissingResourceException ignored) { }
            COUNTRY_NAMES.put(normalize(country.getDisplayCountry(Locale.ENGLISH)), code);
            COUNTRY_NAMES.put(normalize(country.getDisplayCountry(LOCALE)), code);
        }
        countryAlias("United States of America", "US");
        countryAlias("United States", "US");
        countryAlias("UK", "GB");
        countryAlias("Republic of Korea", "KR");
        countryAlias("Korea (Republic of)", "KR");
        countryAlias("Korea (Democratic People's Republic of)", "KP");
        countryAlias("Russian Federation", "RU");
        countryAlias("Viet Nam", "VN");
        countryAlias("Lao People's Democratic Republic", "LA");
        countryAlias("Iran (Islamic Republic of)", "IR");
        countryAlias("Syrian Arab Republic", "SY");
        countryAlias("Moldova (Republic of)", "MD");
        countryAlias("Venezuela (Bolivarian Republic of)", "VE");
        countryAlias("Bolivia (Plurinational State of)", "BO");
        countryAlias("Tanzania, United Republic of", "TZ");
        for (String code : Locale.getISOLanguages()) {
            Locale language = Locale.forLanguageTag(code);
            LANGUAGE_NAMES.put(normalize(language.getDisplayLanguage(Locale.ENGLISH)), language.getDisplayLanguage(LOCALE));
            LANGUAGE_NAMES.put(normalize(code), language.getDisplayLanguage(LOCALE));
            try { LANGUAGE_NAMES.put(normalize(language.getISO3Language()), language.getDisplayLanguage(LOCALE)); }
            catch (MissingResourceException ignored) { }
        }
        LANGUAGE_NAMES.put("mandarin", "Tiếng Trung");
        LANGUAGE_NAMES.put("mandarin chinese", "Tiếng Trung");
        LANGUAGE_NAMES.put("chichewa", "Tiếng Chewa");
        LANGUAGE_NAMES.put("moldovan", "Tiếng Romania");
        for (Currency currency : Currency.getAvailableCurrencies()) {
            CURRENCY_NAMES.put(normalize(currency.getCurrencyCode()), currency);
            CURRENCY_NAMES.put(normalize(currency.getDisplayName(Locale.ENGLISH)), currency);
        }
    }

    private VietnameseText() { }

    public static String country(String name, String code) {
        String known = code == null ? null : COUNTRY_CODES.get(code.trim().toUpperCase(Locale.ROOT));
        if (known == null && name != null) {
            known = COUNTRY_CODES.get(name.trim().toUpperCase(Locale.ROOT));
            if (known == null) known = COUNTRY_NAMES.get(normalize(name));
        }
        return known == null ? value(name) : new Locale("", known).getDisplayCountry(LOCALE);
    }

    public static String country(String name) { return country(name, null); }

    public static String city(String name) {
        if (name == null || name.isBlank()) return UNKNOWN;
        String normalized = normalize(name);
        if (normalized.equals("dalat") || normalized.equals("saigon")) return name.trim();
        String translated = CITIES.get(normalized);
        if (translated != null) return translated;
        // Một tên thành phố có thể chứa dấu phẩy, ví dụ Washington, D.C.
        if (name.contains(",") && !name.toLowerCase(Locale.ROOT).contains("d.c.")) {
            return list(name, part -> CITIES.getOrDefault(normalize(part), part));
        }
        return name.trim();
    }

    public static String city(String name, String countryCode) {
        if (name == null || name.isBlank()) return UNKNOWN;
        String code = countryCode == null ? null : COUNTRY_CODES.get(countryCode.trim().toUpperCase(Locale.ROOT));
        if (code == null && countryCode != null) code = COUNTRY_NAMES.get(normalize(countryCode));
        String expected = CITY_COUNTRIES.get(normalize(name));
        if (expected != null && code != null) {
            return expected.equals(code) ? CITIES.get(normalize(name)) : name.trim();
        }
        return city(name);
    }

    public static String location(String name) {
        if (name == null || name.isBlank()) return UNKNOWN;
        int comma = name.lastIndexOf(',');
        return comma < 0 ? city(name) : city(name.substring(0, comma), name.substring(comma + 1))
                + ", " + country(name.substring(comma + 1));
    }

    public static String currency(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        Currency known = CURRENCY_NAMES.get(normalize(raw));
        if (known != null) return known.getDisplayName(LOCALE) + " (" + known.getCurrencyCode() + ")";
        StringJoiner result = new StringJoiner(", ");
        // Dấu phẩy trong ngoặc phân cách mã/ký hiệu; dấu phẩy ngoài ngoặc phân cách các tiền tệ.
        for (String entry : raw.split(",(?![^()]*\\))")) {
            entry = entry.trim();
            Matcher matcher = CURRENCY_CODE.matcher(entry);
            if (matcher.find()) {
                try {
                    result.add(Currency.getInstance(matcher.group(1)).getDisplayName(LOCALE)
                            + " " + matcher.group() + entry.substring(matcher.end()));
                    continue;
                } catch (IllegalArgumentException ignored) { }
            }
            Currency currency = CURRENCY_NAMES.get(normalize(entry));
            result.add(currency == null ? entry : currency.getDisplayName(LOCALE) + " (" + currency.getCurrencyCode() + ")");
        }
        return result.toString();
    }

    public static String languages(String raw) { return list(raw, part -> LANGUAGE_NAMES.getOrDefault(normalize(part), part)); }
    public static String region(String raw) { return translated(raw, REGIONS); }
    public static String borders(String raw) { return list(raw, VietnameseText::country); }
    public static String weather(String raw) { return translated(raw, WEATHER); }

    public static String errorMessage(Throwable error) {
        Throwable cause = error;
        while (cause != null && cause.getCause() != null
                && (cause instanceof java.util.concurrent.ExecutionException
                || cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.lang.reflect.InvocationTargetException)) {
            cause = cause.getCause();
        }
        if (cause instanceof java.net.SocketTimeoutException) return "Hết thời gian chờ phản hồi của máy chủ.";
        if (cause instanceof java.net.ConnectException) return "Không kết nối được máy chủ. Hãy kiểm tra máy chủ đã chạy chưa.";
        if (cause instanceof java.net.UnknownHostException) return "Không xác định được địa chỉ máy chủ.";
        if (cause instanceof java.util.concurrent.CancellationException) return "Đã hủy yêu cầu.";
        String message = cause == null ? null : cause.getMessage();
        if (message != null && message.matches("(?s).*[^\\x00-\\x7F].*")) return message;
        return "Không hoàn thành được yêu cầu. Hãy thử lại.";
    }

    /** Đổi định dạng ngày, giữ nguyên ý nghĩa múi giờ của dữ liệu gốc. */
    public static String dateTime(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        try { return DATE_TIME.format(Instant.parse(raw).atZone(ZoneOffset.UTC)); }
        catch (RuntimeException ignored) { }
        try { return DATE_TIME.format(OffsetDateTime.parse(raw)); }
        catch (RuntimeException ignored) { }
        try { return DATE_TIME.format(LocalDateTime.parse(raw.replace(' ', 'T'))); }
        catch (RuntimeException ignored) { }
        return raw;
    }

    private static String translated(String raw, Map<String, String> dictionary) {
        return raw == null || raw.isBlank() ? UNKNOWN : dictionary.getOrDefault(normalize(raw), raw.trim());
    }
    private static String list(String raw, Function<String, String> translate) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        StringJoiner output = new StringJoiner(", ");
        for (String part : raw.split(",\\s*")) if (!part.isBlank()) output.add(translate.apply(part.trim()));
        return output.length() == 0 ? UNKNOWN : output.toString();
    }
    private static String value(String text) { return text == null || text.isBlank() ? UNKNOWN : text.trim(); }
    private static void countryAlias(String name, String code) { COUNTRY_NAMES.put(normalize(name), code); }
    private static void cityCountries(String code, String names) {
        for (String name : names.split("\\|")) CITY_COUNTRIES.put(normalize(name), code);
    }
    private static Map<String, String> translations(String source) {
        Map<String, String> dictionary = new HashMap<>();
        for (String line : source.split("\\R")) {
            String[] parts = line.split("\\|", 2);
            if (parts.length == 2) dictionary.put(normalize(parts[0]), parts[1].trim());
        }
        return dictionary;
    }
    private static String normalize(String text) {
        return Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replaceAll("\\s+", " ");
    }
}
