package com.codegym.locketclone.common;

import java.util.LinkedHashSet;
import java.util.Set;

public final class PhoneNumberUtils {
    private PhoneNumberUtils() {
    }

    public static String normalize(String rawPhoneNumber) {
        if (rawPhoneNumber == null || rawPhoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number must not be blank");
        }

        String sanitized = sanitize(rawPhoneNumber);
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Phone number must not be blank");
        }

        if (sanitized.startsWith("00")) {
            sanitized = "+" + sanitized.substring(2);
        }

        if (sanitized.startsWith("+")) {
            String digits = sanitized.substring(1);
            validateDigits(digits);
            return "+" + digits;
        }

        validateDigits(sanitized);

        if (sanitized.startsWith("0") && sanitized.length() == 10) {
            return "+84" + sanitized.substring(1);
        }

        if (sanitized.startsWith("84") && sanitized.length() == 11) {
            return "+" + sanitized;
        }

        if (sanitized.length() >= 10 && sanitized.length() <= 15) {
            return "+" + sanitized;
        }

        throw new IllegalArgumentException("Unsupported phone number format: " + rawPhoneNumber);
    }

    public static Set<String> lookupCandidates(String rawPhoneNumber) {
        String normalized = normalize(rawPhoneNumber);
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);

        String sanitized = sanitize(rawPhoneNumber);
        if (!sanitized.isBlank()) {
            candidates.add(sanitized);
        }

        if (normalized.startsWith("+")) {
            candidates.add(normalized.substring(1));
        }

        if (normalized.startsWith("+84") && normalized.length() > 3) {
            candidates.add("0" + normalized.substring(3));
        }

        return candidates;
    }

    private static String sanitize(String rawPhoneNumber) {
        String trimmed = rawPhoneNumber.trim();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char current = trimmed.charAt(i);
            if (Character.isDigit(current)) {
                builder.append(current);
                continue;
            }
            if (current == '+' && builder.isEmpty()) {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private static void validateDigits(String digits) {
        if (digits.isBlank() || !digits.matches("\\d{10,15}")) {
            throw new IllegalArgumentException("Phone number must contain 10 to 15 digits");
        }
    }
}

