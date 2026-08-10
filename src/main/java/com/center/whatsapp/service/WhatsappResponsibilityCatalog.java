package com.center.whatsapp.service;

import java.util.List;
import java.util.Set;

/**
 * Every distinct WhatsApp send purpose a connected number can be responsible for.
 * Each purpose is assigned to at most one number (see {@code whatsapp_responsibility});
 * at send time the assigned - and connected - number is used, with automatic
 * failover to a backup number if it drops.
 */
public final class WhatsappResponsibilityCatalog {

    public record Responsibility(String code, String label, String description) {
    }

    public static final List<Responsibility> ALL = List.of(
            new Responsibility("student_verification", "رمز تحقق تسجيل الطالب",
                    "يُرسَل رمز التحقق عند إنشاء الطالب لحساب جديد."),
            new Responsibility("student_password_reset", "إعادة تعيين كلمة مرور الطالب",
                    "يُرسَل رمز إعادة تعيين كلمة المرور لحساب الطالب."),
            new Responsibility("parent_password_reset", "إعادة تعيين كلمة مرور ولي الأمر",
                    "يُرسَل رمز إعادة تعيين كلمة المرور لحساب ولي الأمر."),
            new Responsibility("parent_link_approved_wa", "تأكيد ربط ولي الأمر",
                    "يُرسَل لولي الأمر عند قبول ربط حسابه بالطالب."),
            new Responsibility("parent_link_rejected_wa", "رفض ربط ولي الأمر",
                    "يُرسَل لولي الأمر عند تعذّر التحقق من صلته بالطالب."),
            new Responsibility("exam_result", "إرسال نتيجة الاختبار لولي الأمر",
                    "يُرسَل لولي الأمر نتيجة الطالب بعد كل اختبار."),
            new Responsibility("broadcast", "الرسائل الجماعية عبر واتساب",
                    "الرسائل الجماعية التي يرسلها المشرف للطلاب أو أولياء الأمور عبر واتساب."));

    public static final Set<String> CODES =
            Set.copyOf(ALL.stream().map(Responsibility::code).toList());

    public static boolean isValid(String code) {
        return CODES.contains(code);
    }

    private WhatsappResponsibilityCatalog() {
    }
}
