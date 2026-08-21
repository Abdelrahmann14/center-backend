package com.center.common.util;

import java.time.LocalTime;
import java.time.temporal.TemporalAccessor;

/**
 * How this system writes a clock time to a person: ٤ م, ٤:٢٩ م, ٥:٣٠ ص.
 *
 * <p><b>One rule, one place.</b> Every surface that shows a time to an Egyptian
 * reader - the WhatsApp templates, the barcode card, the student report, the
 * invoice, the analytics labels - used to format it independently, and they
 * disagreed: "16:00", "16:00:00", "٤ م". Three of them had their own private
 * copy of the conversion. A rule with three copies is a rule that drifts, which
 * is exactly what happened.
 *
 * <p>This does NOT cover the wire. {@code GroupMapper} still emits "HH:mm" and
 * must keep doing so - that string is parsed by the browser and stored in a
 * {@code time} column, and it is not read by anybody. The distinction is the
 * whole point: machine-readable and human-readable are different jobs, and the
 * bug was one function trying to do both.
 */
public final class ArabicFormat {

    private ArabicFormat() {
    }

    /**
     * The 12-hour Arabic reading, minutes dropped when they are zero.
     *
     * <p>Zero minutes go because "four in the afternoon" is how the hour is
     * said; "٤:٠٠ م" is a clock face transcribed, not speech.
     */
    public static String time(TemporalAccessor value) {
        LocalTime t = localTime(value);
        if (t == null) {
            return "";
        }
        int twelve = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        String body = t.getMinute() == 0
                ? String.valueOf(twelve)
                : String.format("%d:%02d", twelve, t.getMinute());
        return digits(body) + " " + period(t);
    }

    /** The same reading, keeping seconds - for a log or an "exact" variable. */
    public static String timeExact(TemporalAccessor value) {
        LocalTime t = localTime(value);
        if (t == null) {
            return "";
        }
        int twelve = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        return digits(String.format("%d:%02d:%02d", twelve, t.getMinute(), t.getSecond()))
                + " " + period(t);
    }

    /** Western digits rewritten as Arabic-Indic ones (٠-٩). Everything else is kept. */
    public static String digits(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            out.append(c >= '0' && c <= '9' ? (char) ('٠' + (c - '0')) : c);
        }
        return out.toString();
    }

    private static String period(LocalTime t) {
        return t.getHour() < 12 ? "ص" : "م";
    }

    /**
     * The wall-clock part of whatever was handed over.
     *
     * <p>Accepts a {@code TemporalAccessor} rather than a {@code LocalTime} so a
     * caller holding an {@code OffsetDateTime} or a {@code LocalDateTime} does
     * not have to remember to convert - forgetting that conversion is how a
     * time ends up printed in UTC on a page read in Cairo. Callers whose value
     * is an instant must still shift it to Cairo BEFORE calling: this reads the
     * clock fields it is given and does not invent a zone.
     */
    private static LocalTime localTime(TemporalAccessor value) {
        if (value == null) {
            return null;
        }
        return value instanceof LocalTime t ? t : LocalTime.from(value);
    }
}
