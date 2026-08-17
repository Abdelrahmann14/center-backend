-- Assistant permission scope.
--
-- Assistants may be granted only: Students (view/add/edit/delete), Lessons
-- (view/add/edit/delete), Lesson Registration (one access covering the whole
-- process) and Financials (one access covering the whole section). Everything
-- else - Statistics, Groups & Centers, Assistants, Notifications & Messages,
-- Services, and Exams - is admin-only and never delegatable.
--
-- The module re-pointing (STUDENT_ANALYTICS -> ANALYTICS, STUDENT_REPORT_SEND ->
-- NOTIFICATIONS), the admin_managed flips (EXAMS, NOTIFICATIONS -> false) and the
-- FINANCE_VIEW relabel are applied by ModuleCatalogSyncRunner on boot, which
-- updates existing rows. This migration only removes what the catalog no longer
-- carries and clears grants that are no longer assignable.

-- Registration and Finance collapsed to a single access permission each. Drop the
-- per-action codes and any assistant grants that referenced them.
DELETE FROM user_permissions
WHERE permission_id IN (
    SELECT id FROM permissions WHERE code IN (
        'REGISTRATION_CREATE', 'REGISTRATION_UPDATE', 'REGISTRATION_DELETE',
        'FINANCE_ENTRY_MANAGE', 'FINANCE_INVOICE_SEND'));

DELETE FROM permissions WHERE code IN (
    'REGISTRATION_CREATE', 'REGISTRATION_UPDATE', 'REGISTRATION_DELETE',
    'FINANCE_ENTRY_MANAGE', 'FINANCE_INVOICE_SEND');

-- These permissions survive (the admin still holds them) but move under admin-only
-- modules, so no assistant may keep them. Clear any existing assistant grants -
-- they would be inert once the module is admin_managed = false, but leaving them
-- would misrepresent what the assistant can do.
DELETE FROM user_permissions
WHERE permission_id IN (
    SELECT id FROM permissions WHERE code IN (
        'STUDENT_ANALYTICS', 'STUDENT_REPORT_SEND', 'NOTIFICATION_SEND',
        'EXAM_CREATE', 'EXAM_UPDATE', 'EXAM_DELETE', 'EXAM_PUBLISH'));
