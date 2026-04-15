package team.kitemc.verifymc.util;

import java.util.regex.Pattern;

public class PhoneUtil {

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String normalizePhoneNumber(String phone) {
        if (phone == null) return "";
        return phone.trim().replaceAll("[\\s\\-]", "");
    }

    public static String buildFullPhoneNumber(String countryCode, String phone) {
        if (countryCode == null || phone == null) return "";
        return countryCode + phone;
    }

    public static boolean isValidPhoneNumber(String phone, String phoneRegex) {
        if (phone == null || phone.trim().isEmpty()) return false;
        String cleaned = normalizePhoneNumber(phone);
        if (cleaned.isEmpty()) return false;
        try {
            return Pattern.compile(phoneRegex).matcher(cleaned).matches();
        } catch (Exception e) {
            return false;
        }
    }
}
