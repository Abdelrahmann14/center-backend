package com.center.notification.service;
import com.center.center.entity.Center;

import java.util.List;

/**
 * The set of {placeholder} variables an admin may drop into a message or
 * notification. This is the single source of truth: the composer's @-menu, the
 * per-recipient render, and the empty-base map that keeps unmatched variables
 * from leaking all read from here. Each variable carries an example value shown
 * in the editor tooltip so the author sees what it will be replaced with.
 */
public final class VariableCatalog {

    public record Variable(String key, String description, String group, String example) {
    }

    public static final List<Variable> ALL = List.of(
            new Variable("now", "تاريخ ووقت اليوم", "التاريخ والوقت", "٢٩‏/٦‏/٢٠٢٦، ٧:٥٥ م"),
            new Variable("date", "تاريخ اليوم", "التاريخ والوقت", "٢٩‏/٦‏/٢٠٢٦"),
            new Variable("time", "الوقت الحالي", "التاريخ والوقت", "٧:٥٥ م"),
            new Variable("day", "اليوم", "التاريخ والوقت", "الأحد"),
            new Variable("student.name", "اسم الطالب", "الطالب", "أحمد محمد"),
            new Variable("student.phone", "هاتف الطالب", "الطالب", "01012345678"),
            new Variable("student.grade", "صف الطالب", "الطالب", "الصف الأول الثانوي"),
            new Variable("student.serial", "الرقم التعريفي للطالب", "الطالب", "1042"),
            new Variable("parent.name", "اسم ولي الأمر", "ولي الأمر", "محمد علي"),
            new Variable("parent.phone", "هاتف ولي الأمر", "ولي الأمر", "01087654321"),
            new Variable("teacher.name", "اسم المدرّس", "المدرّس", "أ. خالد"),
            new Variable("exam.name", "اسم الاختبار", "الاختبار", "اختبار الوحدة الأولى"),
            new Variable("exam.score", "درجة الطالب", "الاختبار", "18"),
            new Variable("exam.max", "الدرجة العظمى", "الاختبار", "20"),
            new Variable("exam.bonus", "إضافة البونص إن وُجد", "الاختبار", "‏ (+٢ بونص)"),
            new Variable("sender", "اسم المُرسِل", "عام", "Center System"));

    public static List<String> keys() {
        return ALL.stream().map(Variable::key).toList();
    }

    private VariableCatalog() {
    }
}
