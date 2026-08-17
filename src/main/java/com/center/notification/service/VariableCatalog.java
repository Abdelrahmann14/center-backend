package com.center.notification.service;

import java.util.List;

/**
 * The set of {placeholder} variables an admin may drop into a message or
 * notification. This is the single source of truth: the composer's picker, the
 * per-recipient render, and the empty-base map that keeps unmatched variables
 * from leaking all read from here.
 *
 * <p>Every variable carries a {@code label} - two or three plain words, and the
 * ONLY part of it the author ever sees. The composer renders an inserted variable
 * as a chip reading "اسم الطالب", never {@code {student.name}}: the key is a
 * storage detail, and a teacher writing to a parent has no reason to meet it. The
 * longer {@code description} is the tooltip, and {@code example} shows what the
 * chip becomes once the message is actually sent.
 *
 * <p>Adding a variable here is half the job - {@code MessageText} has to be able
 * to fill it. A key nobody fills renders as nothing, which reads to the author as
 * a bug in their message rather than a gap in ours.
 */
public final class VariableCatalog {

    /**
     * @param key         the stored token, e.g. {@code student.name}
     * @param label       what the author sees on the chip: 2-3 words, no jargon
     * @param description the tooltip, one short line
     * @param group       the section it appears under in the picker
     * @param example     a sample of what it renders to
     */
    public record Variable(String key, String label, String description, String group, String example) {
    }

    private static final String G_TIME = "التاريخ والوقت";
    private static final String G_STUDENT = "الطالب";
    private static final String G_PARENT = "ولي الأمر";
    private static final String G_LESSON = "الحصة والمجموعة";
    private static final String G_ATTENDANCE = "الحضور";
    private static final String G_EXAM = "الاختبار";
    private static final String G_TEACHER = "المدرّس";

    public static final List<Variable> ALL = List.of(
            // ── Date & time ────────────────────────────────────────────────
            new Variable("date", "تاريخ اليوم", "تاريخ إرسال الرسالة", G_TIME, "٢٠٢٦-٠٨-١٦"),
            new Variable("time", "وقت الإرسال", "الساعة التي أُرسلت فيها الرسالة", G_TIME, "١٦:٣٠"),
            new Variable("day", "اسم اليوم", "اليوم الذي أُرسلت فيه الرسالة", G_TIME, "الأحد"),
            new Variable("now", "التاريخ والوقت", "التاريخ والساعة معاً", G_TIME, "٢٠٢٦-٠٨-١٦ ١٦:٣٠"),

            // ── Student ────────────────────────────────────────────────────
            new Variable("student.name", "اسم الطالب", "اسم الطالب كاملاً", G_STUDENT, "أحمد محمد علي حسن"),
            new Variable("student.first_name", "الاسم الأول", "أول مقطع من اسم الطالب", G_STUDENT, "أحمد"),
            new Variable("student.serial", "كود الطالب", "الرقم التعريفي المطبوع على الباركود", G_STUDENT, "1042"),
            new Variable("student.phone", "هاتف الطالب", "أول رقم مسجّل للطالب", G_STUDENT, "01012345678"),
            new Variable("student.grade", "الصف الدراسي", "صف الطالب", G_STUDENT, "الصف الأول الثانوي"),
            new Variable("student.school", "مدرسة الطالب", "المدرسة المسجّلة للطالب", G_STUDENT, "مدرسة النيل"),
            new Variable("student.area", "المنطقة السكنية", "منطقة سكن الطالب", G_STUDENT, "المعادي"),
            new Variable("student.gender", "نوع الطالب", "ذكر أو أنثى", G_STUDENT, "ذكر"),
            new Variable("student.track", "الشعبة", "علمي علوم / علمي رياضة / أدبي", G_STUDENT, "علمي علوم"),
            new Variable("student.price", "سعر الحصة", "ما يدفعه الطالب في الحصة", G_STUDENT, "50"),

            // ── Parent ─────────────────────────────────────────────────────
            new Variable("parent.name", "اسم ولي الأمر", "اسم ولي أمر الطالب", G_PARENT, "محمد علي"),
            new Variable("parent.phone", "هاتف ولي الأمر", "أول رقم مسجّل لولي الأمر", G_PARENT, "01087654321"),

            // ── Lesson & group ─────────────────────────────────────────────
            new Variable("group", "المجموعة", "مجموعة الطالب كاملة", G_LESSON, "السبت ١٦:٠٠ - المركز"),
            new Variable("group.day", "يوم المجموعة", "يوم انعقاد المجموعة", G_LESSON, "السبت"),
            new Variable("group.time", "ميعاد المجموعة", "ساعة بداية المجموعة", G_LESSON, "١٦:٠٠"),
            new Variable("center.name", "اسم السنتر", "السنتر الذي تُعقد فيه المجموعة", G_LESSON, "سنتر النور"),
            new Variable("lesson.name", "اسم الحصة", "عنوان الحصة", G_LESSON, "حصة الوحدة الأولى"),
            new Variable("lesson.homework", "واجب الحصة", "الواجب المطلوب في هذه الحصة", G_LESSON, "مسائل ص ٣٢"),

            // ── Attendance ─────────────────────────────────────────────────
            new Variable("attendance.status", "حالة الحضور", "حاضر أو غائب", G_ATTENDANCE, "حاضر"),
            new Variable("attendance.date", "تاريخ الحضور", "اليوم الذي حضر فيه الطالب", G_ATTENDANCE, "٢٠٢٦-٠٨-١٦"),
            new Variable("attendance.time", "وقت الحضور", "ساعة تسجيل الحضور", G_ATTENDANCE, "١٦:٠٣"),
            new Variable("attendance.time_exact", "وقت الحضور بالثانية",
                    "لحظة تسجيل الحضور بالضبط، بالثواني", G_ATTENDANCE, "١٦:٠٣:٢٧"),
            new Variable("homework.status", "حالة الواجب", "حل الواجب أو لم يحله", G_ATTENDANCE, "لم يحل الواجب"),

            // ── Exam ───────────────────────────────────────────────────────
            new Variable("exam.name", "اسم الاختبار", "عنوان اختبار الحصة", G_EXAM, "اختبار الوحدة الأولى"),
            new Variable("exam.score", "درجة الطالب", "الدرجة التي حصل عليها", G_EXAM, "18"),
            new Variable("exam.max", "الدرجة العظمى", "درجة الاختبار كاملة", G_EXAM, "20"),
            new Variable("exam.percent", "نسبة الدرجة", "درجة الطالب كنسبة مئوية", G_EXAM, "90%"),

            // ── Teacher ────────────────────────────────────────────────────
            new Variable("teacher.name", "اسم المدرّس", "صاحب المركز الذي تُرسل الرسالة باسمه", G_TEACHER, "أ. خالد"),
            new Variable("sender", "اسم المُرسِل", "الاسم الظاهر في توقيع الرسالة", G_TEACHER, "أ. خالد"));

    public static List<String> keys() {
        return ALL.stream().map(Variable::key).toList();
    }

    private VariableCatalog() {
    }
}
