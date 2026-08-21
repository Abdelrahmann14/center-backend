package com.center.whatsapp.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every distinct WhatsApp send purpose a connected number can be responsible for.
 * Each purpose is assigned to at most one number (see {@code whatsapp_responsibility});
 * at send time the assigned - and connected - number is used, with automatic
 * failover to a backup number if it drops.
 *
 * <p>Every entry is a message a teacher recognises and chooses a number for:
 * attendance, absence, exam grade, report, barcode, and whatever they write by
 * hand. There is no hidden plumbing left - the verification codes, password
 * resets and guardian-link notices that used to sit here belonged to the mobile
 * app's own accounts, and went with them.
 *
 * <p>{@link #forOrigin} is the bridge from the message log's {@code origin} to a
 * code here. Every send in the messaging feature carries an origin, and routing
 * by it is what makes "الحضور من رقم والغياب من رقم آخر" a real setting instead
 * of a label - before this, every message left through {@code broadcast}.
 */
public final class WhatsappResponsibilityCatalog {

    /**
     * @param carriesFile whether this message ships a PDF, which means its
     *                    template needs a DOCUMENT header
     */
    public record Responsibility(String code, String label, String description,
            boolean carriesFile) {
    }

    public static final String ATTENDANCE = "attendance";
    public static final String ABSENCE = "absence";
    public static final String EXAM = "exam_result";
    public static final String REPORT = "report";
    public static final String BARCODE = "barcode";

    /** Anything the teacher writes and sends by hand, plus the broadcast screen. */
    public static final String BROADCAST = "broadcast";

    public static final List<Responsibility> ALL = List.of(
            new Responsibility(ATTENDANCE, "رسالة الحضور",
                    "تُرسَل لحظة تسجيل حضور الطالب في الحصة.", false),
            new Responsibility(ABSENCE, "رسالة الغياب",
                    "تُرسَل للطلاب الغائبين في نهاية الأسبوع المحدد.", false),
            new Responsibility(EXAM, "رسالة الدرجة",
                    "تُرسَل لولي الأمر نتيجة الطالب بعد كل اختبار.", false),
            new Responsibility(REPORT, "تقرير الطالب",
                    "ملف تقرير الطالب (PDF) مع نص التقرير.", true),
            new Responsibility(BARCODE, "كارت الباركود",
                    "كارت الطالب (PDF) عند إضافته لأول مرة أو عند إعادة إرساله.", true),
            new Responsibility(BROADCAST, "الرسائل الجماعية والرسائل اليدوية",
                    "الرسائل التي يكتبها المشرف ويرسلها للطلاب أو أولياء الأمور.", false));

    public static final Set<String> CODES =
            Set.copyOf(ALL.stream().map(Responsibility::code).toList());

    /**
     * The message log's {@code origin} to the purpose that routes it.
     *
     * <p>NEW_STUDENT and BARCODE both ship the same barcode card - one on its own
     * because the student was just added, the other because someone pressed the
     * button - so they share one number and one template. Splitting them would
     * make the teacher answer the same question twice.
     */
    private static final Map<String, String> BY_ORIGIN = Map.of(
            "ATTENDANCE", ATTENDANCE,
            "ABSENCE", ABSENCE,
            "EXAM_GRADE", EXAM,
            "REPORT", REPORT,
            "NEW_STUDENT", BARCODE,
            "BARCODE", BARCODE,
            "MANUAL", BROADCAST,
            "INVOICE", BROADCAST);

    /** The purpose that routes one origin; {@code broadcast} for anything unmapped. */
    public static String forOrigin(String origin) {
        return origin == null ? BROADCAST : BY_ORIGIN.getOrDefault(origin, BROADCAST);
    }

    /**
     * The Arabic name of the message type an origin belongs to.
     *
     * <p>For the row a send leaves behind when no template is bound yet: there
     * is no wording to record, and the type's own name at least says what was
     * being attempted. An empty body would say nothing at all.
     */
    public static String labelForOrigin(String origin) {
        Responsibility r = find(forOrigin(origin));
        return r == null ? "رسالة" : r.label();
    }

    public static boolean isValid(String code) {
        return CODES.contains(code);
    }

    public static Responsibility find(String code) {
        return ALL.stream().filter(r -> r.code().equals(code)).findFirst().orElse(null);
    }

    private WhatsappResponsibilityCatalog() {
    }
}
