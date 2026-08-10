package com.center.common.util;

/** Small text helpers shared across the services. */
public final class TextUtils {

    private TextUtils() {
    }

    /** Trims, then treats an empty result as absent - the columns are nullable. */
    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Keeps only digits, so "0100 123 4567" and "01001234567" compare equal. */
    public static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
