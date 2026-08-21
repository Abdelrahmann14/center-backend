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

    /**
     * Every module is a screen this system actually has, and every permission
     * gates something that actually exists.
     *
     * <p>The {@code adminManaged} flag decides delegation, not visibility. Both
     * kinds of module are platform-controlled screens the super admin can switch
     * on/off per workspace; the flag says whether an admin may hand the screen's
     * permissions to an assistant:
     * <ul>
     *   <li><b>adminManaged = true</b> - assistant-assignable. Its permissions
     *       appear in the grant editor and an assistant can be given them:
     *       STUDENTS, LESSONS, REGISTRATIONS, FINANCE, ASSISTANT_ATTENDANCE,
     *       NOTIFICATIONS (the Messages page).</li>
     *   <li><b>adminManaged = false</b> - admin-only. The admin still holds every
     *       permission of the module (see {@code findAdminPermissionCodes}), so
     *       enforcement is unchanged, but the codes never show in the editor and
     *       an assistant can never hold them: ANALYTICS, GROUPS, ASSISTANTS,
     *       EXAMS.</li>
     * </ul>
     *
     * <p>So making a capability admin-only means parking its permission under an
     * admin-only module. That is why STUDENT_ANALYTICS and STUDENT_REPORT_SEND both
     * live under ANALYTICS rather than under STUDENTS: the student screen is
     * delegatable, those two actions are not.
     *
     * <p>Registration and Finance each expose a single "access" permission that
     * covers everything inside the section - an assistant is either trusted with
     * the whole process or not, so the old per-action split (register/edit/delete,
     * view/manage/send) is gone.
     *
     * <p>WhatsApp is not here because it already has its own per-admin switch
     * ({@code whatsapp_config}); Google Contacts has no switch at all - it costs
     * nothing to run, so nobody is gated on it.
     */
    public static final List<ModuleDef> MODULES = List.of(
            // ── Assistant-assignable (admin-managed) ──
            new ModuleDef("STUDENTS", "الطلاب", "إدارة بيانات الطلاب", "workspace", true, true, true, 10, List.of(
                    p("STUDENT_VIEW", "VIEW", "عرض الطلاب", 0),
                    p("STUDENT_CREATE", "CREATE", "إضافة طالب", 1),
                    p("STUDENT_UPDATE", "UPDATE", "تعديل طالب", 2),
                    p("STUDENT_DELETE", "DELETE", "حذف طالب", 3),
                    // Every action on the student screen is now delegatable, so
                    // these two live here rather than parked under an admin-only
                    // module. The sync runner re-parents the existing rows on boot
                    // (it sets each permission's module_id from this list), so no
                    // migration is needed and existing grants keep their ids.
                    p("STUDENT_ANALYTICS", "VIEW", "عرض تقرير الطالب", 4),
                    p("STUDENT_REPORT_SEND", "SEND", "إرسال تقرير/باركود الطالب", 5))),
            new ModuleDef("LESSONS", "الحصص", "إنشاء وإدارة الحصص", "workspace", true, true, true, 20, List.of(
                    p("LESSON_VIEW", "VIEW", "عرض الحصص", 0),
                    p("LESSON_CREATE", "CREATE", "إضافة حصة", 1),
                    p("LESSON_UPDATE", "UPDATE", "تعديل حصة", 2),
                    p("LESSON_DELETE", "DELETE", "حذف حصة", 3))),
            new ModuleDef("REGISTRATIONS", "تسجيل الحصة", "تسجيل حضور الطلاب في الحصص", "workspace", true, true, true, 30, List.of(
                    p("REGISTRATION_ACCESS", "ACCESS", "الوصول إلى تسجيل الحصة", 0))),
            new ModuleDef("FINANCE", "الحسابات", "فواتير الحصص والإيرادات والمصروفات", "workspace", true, true, true, 40, List.of(
                    p("FINANCE_VIEW", "ACCESS", "الوصول إلى الحسابات وإدارتها", 0))),
            new ModuleDef("ASSISTANT_ATTENDANCE", "حضور المساعدين", "تسجيل حضور المساعدين للحصص ومتابعته", "workspace", true, true, true, 45, List.of(
                    p("ASSISTANT_ATTENDANCE", "ACCESS", "تسجيل حضور المساعدين وعرضه", 0))),
            // The Messages page (WhatsApp). Reuses the NOTIFICATIONS code so the sync
            // runner updates the existing row in place instead of stranding it. Now
            // delegatable, so an admin may hand sending to an assistant.
            //
            // Three permissions, because they are three different amounts of trust
            // and one code could not tell them apart. Sending is a daily job at the
            // desk. READING the log is knowing every number and every message the
            // workspace has ever sent, which is a lot to hand somebody who only
            // needs to press "attendance". And ERASING it removes the only record
            // of what went out - a receptionist who may send is not thereby
            // somebody who may destroy the evidence of sending.
            new ModuleDef("NOTIFICATIONS", "الرسائل", "محادثات واتساب، الإرسال، وسجل الرسائل", "workspace", true, true, true, 46, List.of(
                    p("NOTIFICATION_SEND", "SEND", "إرسال رسائل واتساب والرد على المحادثات", 0),
                    p("NOTIFICATION_LOG_VIEW", "VIEW", "عرض سجل الرسائل", 1),
                    p("NOTIFICATION_LOG_DELETE", "DELETE", "مسح سجل الرسائل", 2))),
            // ── Admin-only (not delegatable). The admin holds these; they never
            //    appear in the assistant grant editor, and no assistant can get
            //    them: ANALYTICS, GROUPS, ASSISTANTS. ──
            new ModuleDef("EXAMS", "الاختبارات", "بناء ونشر اختبارات الحصص", "workspace", true, false, true, 50, List.of(
                    p("EXAM_CREATE", "CREATE", "إنشاء اختبار", 1),
                    p("EXAM_UPDATE", "UPDATE", "تعديل اختبار", 2),
                    p("EXAM_DELETE", "DELETE", "حذف اختبار", 3),
                    p("EXAM_PUBLISH", "PUBLISH", "نشر اختبار", 4))),
            // The main analytics dashboard stays admin-only and carries no
            // delegatable permission of its own (the screen is gated on the
            // module + admin role). Its former two student-report permissions
            // moved to STUDENTS above.
            new ModuleDef("ANALYTICS", "الإحصائيات", "لوحة التحليلات والإحصاءات", "workspace", true, false, true, 70, List.of()),
            new ModuleDef("GROUPS", "المجموعات والسناتر", "إدارة السناتر ومجموعات الطلاب ومواعيدها", "workspace", true, false, true, 80, List.of()),
            new ModuleDef("ASSISTANTS", "المساعدون", "حسابات المساعدين والصلاحيات", "workspace", true, false, true, 90, List.of()));
}
