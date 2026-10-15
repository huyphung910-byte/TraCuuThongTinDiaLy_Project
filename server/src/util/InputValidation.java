package util;

import java.util.regex.Pattern;

/** Rules shared by the client and server; passwords are never trimmed. */
public final class InputValidation {
    public static final int USERNAME_MAX = 50;
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 128;
    public static final int FULLNAME_MAX = 100;
    public static final int EMAIL_MAX = 100;
    public static final int KEYWORD_MAX = 200;
    public static final int NOTE_MAX = 2000;
    private static final Pattern LOCAL_PART = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+");
    private static final Pattern DOMAIN_LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");

    private InputValidation() { }

    public static final class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) { super(message); }
    }

    public static String username(String value) {
        String result = keyword(value, "Tên đăng nhập", 1, USERNAME_MAX);
        if (result.codePoints().anyMatch(InputValidation::space)) {
            throw invalid("Tên đăng nhập không được chứa khoảng trắng.");
        }
        return result;
    }

    public static String password(String value, String label) {
        if (value == null || blank(value)) throw invalid(label + " không được để trống.");
        int length = value.codePointCount(0, value.length());
        if (length < PASSWORD_MIN || length > PASSWORD_MAX) {
            throw invalid(label + " phải có từ 8 đến 128 ký tự.");
        }
        if (invalidText(value, false)) throw invalid(label + " chứa ký tự không hợp lệ.");
        return value;
    }

    public static String fullname(String value) {
        return keyword(value, "Họ tên", 1, FULLNAME_MAX);
    }

    public static String email(String value) {
        String result = keyword(value, "Email", 1, EMAIL_MAX);
        int separator = result.indexOf('@');
        if (separator < 1 || separator != result.lastIndexOf('@')) throw invalid("Email không đúng định dạng.");
        String local = result.substring(0, separator);
        String domain = result.substring(separator + 1);
        if (local.length() > 64 || !LOCAL_PART.matcher(local).matches()
                || local.startsWith(".") || local.endsWith(".") || local.contains("..")) {
            throw invalid("Email không đúng định dạng.");
        }
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) throw invalid("Email phải có tên miền hợp lệ (ví dụ ten@example.com).");
        for (String label : labels) {
            if (!DOMAIN_LABEL.matcher(label).matches()) throw invalid("Email không đúng định dạng.");
        }
        if (!labels[labels.length - 1].matches("[A-Za-z]{2,63}|[xX][nN]--[A-Za-z0-9-]{2,59}")) {
            throw invalid("Email phải có tên miền hợp lệ (ví dụ ten@example.com).");
        }
        return result;
    }

    public static String keyword(String value, String label, int minimum, int maximum) {
        if (value == null || blank(value)) throw invalid(label + " không được để trống.");
        if (invalidText(value, false)) throw invalid(label + " chứa ký tự không hợp lệ.");
        String result = trim(value);
        int length = result.codePointCount(0, result.length());
        if (length < minimum || length > maximum) {
            throw invalid(label + " phải có từ " + minimum + " đến " + maximum + " ký tự.");
        }
        return result;
    }

    public static String optionalText(String value, String label, int maximum) {
        if (value == null) return "";
        if (invalidText(value, true)) throw invalid(label + " chứa ký tự không hợp lệ.");
        if (value.codePointCount(0, value.length()) > maximum) {
            throw invalid(label + " phải có tối đa " + maximum + " ký tự.");
        }
        return trim(value);
    }

    public static void coordinates(Double latitude, Double longitude) {
        if (latitude == null) throw invalid("Vĩ độ không được để trống.");
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw invalid("Vĩ độ phải là số hữu hạn từ -90 đến 90.");
        }
        if (longitude == null) throw invalid("Kinh độ không được để trống.");
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw invalid("Kinh độ phải là số hữu hạn từ -180 đến 180.");
        }
    }

    public static int positiveId(int value, String label) {
        if (value <= 0) throw invalid(label + " phải là số nguyên dương.");
        return value;
    }

    private static boolean space(int codepoint) {
        return Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint);
    }

    private static boolean blank(String value) {
        return value.isEmpty() || value.codePoints().allMatch(InputValidation::space);
    }

    private static String trim(String value) {
        int start = 0, end = value.length();
        while (start < end && space(value.codePointAt(start))) start += Character.charCount(value.codePointAt(start));
        while (end > start && space(value.codePointBefore(end))) end -= Character.charCount(value.codePointBefore(end));
        return value.substring(start, end);
    }

    private static boolean invalidText(String value, boolean multiline) {
        return value.codePoints().anyMatch(codepoint -> (Character.isISOControl(codepoint)
                && !(multiline && (codepoint == '\n' || codepoint == '\r' || codepoint == '\t')))
                || (codepoint >= 0xD800 && codepoint <= 0xDFFF));
    }

    private static ValidationException invalid(String message) { return new ValidationException(message); }
}
