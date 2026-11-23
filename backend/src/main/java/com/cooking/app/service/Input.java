package com.cooking.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Input {
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");
    private static final Pattern PHONE = Pattern.compile("[0-9]{8,15}");

    private Input() {}

    public static String text(String value, String label, int max) {
        String cleaned = optional(value, label, max);
        if (cleaned == null) throw ApiException.bad("Vui lòng nhập " + label);
        return cleaned;
    }

    public static String optional(String value, String label, int max) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.strip();
        if (cleaned.length() > max) throw ApiException.bad(label + " tối đa " + max + " ký tự");
        return cleaned;
    }

    public static String email(String value) {
        String cleaned = text(value, "email", 254).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(cleaned).matches()) throw ApiException.bad("Email không hợp lệ");
        return cleaned;
    }

    public static String phone(String value) {
        String cleaned = text(value, "số điện thoại", 30).replaceAll("[\\s().-]", "");
        if (cleaned.startsWith("+84")) cleaned = "0" + cleaned.substring(3);
        else if (cleaned.startsWith("+")) cleaned = cleaned.substring(1);
        if (!PHONE.matcher(cleaned).matches()) throw ApiException.bad("Số điện thoại không hợp lệ");
        return cleaned;
    }

    public static void password(String password, String confirmation) {
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72
            || password.chars().noneMatch(Character::isUpperCase) || password.chars().noneMatch(Character::isLowerCase)
            || password.chars().noneMatch(Character::isDigit)
            || password.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))) {
            throw ApiException.bad("Mật khẩu cần ít nhất 8 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt");
        }
        if (!password.equals(confirmation)) throw ApiException.bad("Mật khẩu xác nhận không khớp");
    }

    public static int between(Integer value, int min, int max, String label) {
        if (value == null || value < min || value > max)
            throw ApiException.bad(label + " phải từ " + min + " đến " + max);
        return value;
    }

    public static BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(new BigDecimal("99999999.99")) > 0
            || value.setScale(2, RoundingMode.HALF_UP).signum() == 0)
            throw ApiException.bad("Số lượng phải từ 0,01 đến 99.999.999,99");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static String httpsUrl(String value, String label) {
        String cleaned = optional(value, label, 2048);
        if (cleaned == null) return null;
        try {
            URI uri = URI.create(cleaned);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null)
                return cleaned;
        } catch (IllegalArgumentException ignored) {
            // Invalid URI syntax is reported using the same form error.
        }
        throw ApiException.bad(label + " phải là đường dẫn HTTPS hợp lệ");
    }
}
