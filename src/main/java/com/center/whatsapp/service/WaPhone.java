package com.center.whatsapp.service;

/**
 * Turns a stored phone number into the international digits WhatsApp addresses
 * people by. Numbers are typed into this system the way Egyptians write them -
 * {@code 01xxxxxxxxx} - while WhatsApp wants {@code 201xxxxxxxxx}.
 */
public final class WaPhone {

    private WaPhone() {
    }

    /**
     * The local form the roster stores, so two spellings of one number compare
     * equal: {@code +20 101 234 5678}, {@code 201012345678} and
     * {@code 01012345678} all become {@code 01012345678}.
     *
     * <p>The inverse of {@link #international}. Used as the key wherever a phone
     * has to be looked up rather than dialled - the number-check cache, the
     * reachability map - because those arrive written whichever way the source
     * happened to write them.
     */
    public static String local(String raw) {
        String d = raw == null ? "" : raw.replaceAll("\\D", "");
        if (d.startsWith("20") && d.length() == 12) {
            d = d.substring(2);
        }
        if (!d.isEmpty() && !d.startsWith("0")) {
            d = "0" + d;
        }
        return d;
    }

    /** {@code 01xxxxxxxxx} / {@code +20 1x...} / {@code 201x...} all become {@code 201xxxxxxxxx}. */
    public static String international(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.startsWith("0")) {
            return "20" + digits.substring(1);
        }
        if (!digits.startsWith("20")) {
            return "20" + digits;
        }
        return digits;
    }
}
