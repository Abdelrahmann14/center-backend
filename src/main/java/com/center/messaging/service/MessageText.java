package com.center.messaging.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.center.group.entity.Group;
import com.center.lecture.entity.Lecture;
import com.center.notification.service.VariableCatalog;
import com.center.registration.entity.Registration;
import com.center.student.entity.Student;

/**
 * Renders a message body for WhatsApp: substitutes {placeholders} and turns any
 * leftover {emphasis} the author typed into WhatsApp *bold*. Mirrors the admin
 * composer's own interpolation so the two behave identically.
 *
 * <p>Every key in {@link VariableCatalog} must be fillable from here. The catalog
 * is what the author is offered; this is what those offers turn into. A key the
 * catalog advertises and this file never sets renders as nothing, and the author
 * reasonably reads that as their message being broken.
 */
public final class MessageText {

    public static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    private static final Locale AR = Locale.forLanguageTag("ar");
    private static final Pattern MARKER = Pattern.compile("\\{([^{}]+)\\}");
    private static final String[] DAYS = {"السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة"};

    private MessageText() {
    }

    /** A var map pre-filled with every catalog key blank, so unset ones vanish. */
    public static Map<String, String> baseVars() {
        Map<String, String> map = new HashMap<>();
        for (String key : VariableCatalog.keys()) {
            map.put(key, "");
        }
        return map;
    }

    /** Substitute known vars, then render remaining {x} markers as WhatsApp *x* bold. */
    public static String render(String template, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return MARKER.matcher(out).replaceAll("*$1*");
    }

    /** Arabic day name for a group's stored day index (0 = Saturday .. 6 = Friday). */
    public static String dayName(int index) {
        return index >= 0 && index < DAYS.length ? DAYS[index] : "";
    }

    /** Arabic day name for a java.time day. */
    public static String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, AR);
    }

    /** Time-of-send variables (date/time/day) plus the teacher name and sender. */
    public static Map<String, String> globals(String teacher) {
        LocalDate date = LocalDate.now(CAIRO);
        LocalTime time = LocalTime.now(CAIRO);
        String d = date.toString();
        String t = hhmm(time);
        Map<String, String> m = new HashMap<>();
        m.put("date", d);
        m.put("time", t);
        m.put("now", d + " " + t);
        m.put("day", dayName(date.getDayOfWeek()));
        m.put("teacher.name", nz(teacher));
        m.put("sender", nz(teacher));
        return m;
    }

    /**
     * A full variable map for one student: an empty base, the time/teacher globals,
     * and the student's own fields. Callers add message-specific vars (the lesson,
     * the attendance instant, the exam) on top.
     */
    public static Map<String, String> studentVars(Student s, String teacher) {
        Map<String, String> m = baseVars();
        m.putAll(globals(teacher));
        m.put("student.name", nz(s.getName()));
        m.put("student.first_name", firstWord(s.getName()));
        m.put("student.phone", firstPhone(s.getStudentPhones()));
        m.put("student.grade", nz(s.getGrade()));
        m.put("student.school", nz(s.getSchool()));
        // The field is still called "city" in storage; to everyone using the system
        // it is the residential area, and that is what the variable is called.
        m.put("student.area", nz(s.getCity()));
        m.put("student.gender", s.getGender() == null ? "" : s.getGender().getValue());
        m.put("student.track", s.getAcademicTrack() == null ? "" : s.getAcademicTrack().getValue());
        m.put("student.price", money(s.getLessonPrice()));
        m.put("student.serial", s.getSerial() == null ? "" : String.valueOf(s.getSerial()));
        m.put("parent.phone", firstPhone(s.getParentPhones()));
        putGroup(m, s.getGroup());
        return m;
    }

    /** The group's own variables: the full label, plus its day, time and center. */
    public static void putGroup(Map<String, String> m, Group group) {
        m.put("group", groupLabel(group));
        m.put("group.day", group == null ? "" : dayName(group.getDayOfWeek()));
        m.put("group.time", group == null || group.getStartTime() == null
                ? "" : hhmm(group.getStartTime()));
        m.put("center.name", group == null || group.getCenterName() == null
                ? "" : group.getCenterName());
    }

    /** The lesson's own variables. */
    public static void putLesson(Map<String, String> m, Lecture lecture) {
        m.put("lesson.name", lecture == null || lecture.getName() == null ? "" : lecture.getName());
        m.put("lesson.homework", lecture == null || lecture.getHomework() == null
                ? "" : lecture.getHomework());
    }

    /**
     * The attendance instant, to the minute and to the second.
     *
     * <p>Read off the registration rather than the clock: a message sent from the
     * Lessons page hours after the lesson, or a batch replayed when a device came
     * back online, must still quote when the student actually walked in.
     */
    public static void putAttendance(Map<String, String> m, Registration r, String status) {
        m.put("attendance.status", nz(status));
        if (r == null) {
            return;
        }
        OffsetDateTime at = r.getAttendedAt();
        if (at != null) {
            LocalTime local = at.atZoneSameInstant(CAIRO).toLocalTime();
            m.put("attendance.date", at.atZoneSameInstant(CAIRO).toLocalDate().toString());
            m.put("attendance.time", hhmm(local));
            m.put("attendance.time_exact", hhmmss(local));
        }
        m.put("homework.status", r.getHomeworkFlag() == null
                ? "حل الواجب" : r.getHomeworkFlag().getValue());
    }

    /** The exam variables for one graded registration. */
    public static void putExam(Map<String, String> m, Registration r, Lecture lecture) {
        if (lecture != null) {
            m.put("exam.name", lecture.getExamName() == null ? "" : lecture.getExamName());
            m.put("exam.max", lecture.getExamGrade() == null ? "" : lecture.getExamGrade());
        }
        BigDecimal score = r == null ? null : r.getExamScore();
        if (score == null) {
            return;
        }
        m.put("exam.score", money(score));
        BigDecimal max = firstNumber(lecture == null ? null : lecture.getExamGrade());
        if (max != null && max.signum() > 0) {
            m.put("exam.percent", score.multiply(BigDecimal.valueOf(100))
                    .divide(max, 0, RoundingMode.HALF_UP).toPlainString() + "%");
        }
    }

    /** The first phone in a phones array, or blank. */
    public static String firstPhone(String[] phones) {
        return phones != null && phones.length > 0 && phones[0] != null ? phones[0] : "";
    }

    /** The first space-separated part of a name - what people are called. */
    public static String firstWord(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] parts = name.strip().split("\\s+");
        return parts[0];
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** A price without trailing zeros: 50.00 reads as 50. */
    private static String money(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    /** exam_grade is free text ("20", "20 درجة"); its cap is the first number. */
    private static BigDecimal firstNumber(String raw) {
        if (raw == null) {
            return null;
        }
        var matcher = Pattern.compile("\\d+(\\.\\d+)?").matcher(raw);
        return matcher.find() ? new BigDecimal(matcher.group()) : null;
    }

    private static String hhmm(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private static String hhmmss(LocalTime t) {
        return String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());
    }

    /** A readable group label: "السبت ١٦:٠٠ - المركز". */
    public static String groupLabel(Group group) {
        if (group == null) {
            return "";
        }
        LocalTime t = group.getStartTime();
        String time = t == null ? "" : hhmm(t);
        String day = dayName(group.getDayOfWeek());
        String center = group.getCenterName() == null ? "" : group.getCenterName();
        return (day + " " + time + (center.isBlank() ? "" : " - " + center)).strip();
    }
}
