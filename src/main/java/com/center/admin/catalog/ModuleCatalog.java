package com.center.admin.catalog;

import java.util.List;

/**
 * The single code-side registration point for the RBAC catalog. To add a feature
 * a developer adds one {@link ModuleDef} here (with its permissions); the
 * {@link ModuleCatalogSyncRunner} upserts it into the DB on the next boot, and the
 * admin panel + enforcement pick it up automatically. No enforcement logic reads
 * this list at runtime - the DB is the runtime source of truth; this is the seed
 * and reconciliation source. Keep it in step with {@code V33__rbac.sql}.
 */
public final class ModuleCatalog {

    private ModuleCatalog() {}

    /** A permission action within a module. */
    public record PermissionDef(String code, String action, String nameAr, int sortOrder) {}

    /** A platform feature and its permissions, with its ownership config. */
    public record ModuleDef(
            String code,
            String nameAr,
            String descriptionAr,
            String category,
            boolean platformControlled,
            boolean adminManaged,
            boolean defaultEnabled,
            int sortOrder,
            List<PermissionDef> permissions) {}

    private static PermissionDef p(String code, String action, String nameAr, int sortOrder) {
        return new PermissionDef(code, action, nameAr, sortOrder);
    }

    public static final List<ModuleDef> MODULES = List.of(
            // Admin-managed domain modules: always available (not platform-gated).
            new ModuleDef("STUDENTS", "الطلاب", "إدارة بيانات الطلاب", "workspace", false, true, true, 10, List.of(
                    p("STUDENT_VIEW", "VIEW", "عرض الطلاب", 0),
                    p("STUDENT_CREATE", "CREATE", "إضافة طالب", 1),
                    p("STUDENT_UPDATE", "UPDATE", "تعديل طالب", 2),
                    p("STUDENT_DELETE", "DELETE", "حذف طالب", 3),
                    p("STUDENT_ANALYTICS", "VIEW", "عرض تحليلات الطالب", 4),
                    p("STUDENT_REPORT_SEND", "SEND", "إرسال تقرير الطالب", 5))),
            new ModuleDef("LESSONS", "الحصص", "إنشاء وإدارة الحصص", "workspace", false, true, true, 30, List.of(
                    p("LESSON_VIEW", "VIEW", "عرض الحصص", 0),
                    p("LESSON_CREATE", "CREATE", "إضافة حصة", 1),
                    p("LESSON_UPDATE", "UPDATE", "تعديل حصة", 2),
                    p("LESSON_DELETE", "DELETE", "حذف حصة", 3))),
            new ModuleDef("REGISTRATIONS", "التسجيل والحضور", "تسجيل الطلاب في الحصص والحضور", "workspace", false, true, true, 40, List.of(
                    p("REGISTRATION_ACCESS", "ACCESS", "صفحة تسجيل الحصة", 0),
                    p("ATTENDANCE_ACCESS", "ACCESS", "صفحة تسجيل الحضور", 4),
                    p("REGISTRATION_CREATE", "CREATE", "تسجيل طالب", 1),
                    p("REGISTRATION_UPDATE", "UPDATE", "تعديل تسجيل", 2),
                    p("REGISTRATION_DELETE", "DELETE", "حذف تسجيل", 3))),
            new ModuleDef("EXAMS", "الاختبارات", "بناء ونشر اختبارات الحصص", "workspace", false, true, true, 70, List.of(
                    p("EXAM_CREATE", "CREATE", "إنشاء اختبار", 1),
                    p("EXAM_UPDATE", "UPDATE", "تعديل اختبار", 2),
                    p("EXAM_DELETE", "DELETE", "حذف اختبار", 3),
                    p("EXAM_PUBLISH", "PUBLISH", "نشر اختبار", 4))),
            // Platform modules: gated by the super admin per admin.
            // ANALYTICS carries no permission: the dashboard is the admin's own
            // screen and is never delegated - same for the groups/centers,
            // assistants and integrations screens, which have no module at all.
            new ModuleDef("ANALYTICS", "التحليلات", "لوحة التحليلات والإحصاءات", "platform", true, false, true, 100, List.of()),
            new ModuleDef("NOTIFICATIONS", "الإشعارات", "إرسال الإشعارات", "platform", true, true, true, 110, List.of(
                    p("NOTIFICATION_SEND", "SEND", "إرسال إشعارات", 1))),
            new ModuleDef("WHATSAPP", "واتساب", "تكامل واتساب والرسائل", "platform", true, false, true, 120, List.of(
                    p("WHATSAPP_ACCESS", "ACCESS", "الوصول لواتساب", 1))),
            new ModuleDef("MOBILE_APP", "تطبيق الجوال", "تطبيق الطلاب وأولياء الأمور", "platform", true, false, true, 130, List.of(
                    p("MOBILE_APP_ACCESS", "ACCESS", "الوصول للتطبيق", 1))),
            new ModuleDef("REPORTS", "التقارير", "التقارير التفصيلية", "platform", true, true, false, 140, List.of(
                    p("REPORT_VIEW", "VIEW", "عرض التقارير", 1))),
            new ModuleDef("PAYMENTS", "المدفوعات", "إدارة المدفوعات", "platform", true, true, false, 150, List.of(
                    p("PAYMENT_VIEW", "VIEW", "عرض المدفوعات", 1),
                    p("PAYMENT_MANAGE", "MANAGE", "إدارة المدفوعات", 2))),
            new ModuleDef("WEBSITE", "الموقع الإلكتروني", "الموقع العام", "platform", true, false, false, 160, List.of(
                    p("WEBSITE_ACCESS", "ACCESS", "الوصول للموقع", 1))),
            new ModuleDef("AI", "مزايا الذكاء الاصطناعي", "المزايا الذكية", "platform", true, false, false, 170, List.of(
                    p("AI_ACCESS", "ACCESS", "استخدام الذكاء الاصطناعي", 1))),
            new ModuleDef("AUTOMATION", "الأتمتة", "مسارات العمل الآلية", "platform", true, false, false, 180, List.of(
                    p("AUTOMATION_ACCESS", "ACCESS", "استخدام الأتمتة", 1))));
}
