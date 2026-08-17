-- Prune the RBAC catalog down to features this system actually has.
--
-- The catalog shipped with placeholder modules whose permission codes appear
-- nowhere in the backend, the web app or the mobile app: the super admin was
-- flipping switches wired to nothing, and an admin could grant an assistant a
-- permission that unlocked no screen. ModuleCatalogSyncRunner is additive by
-- design (it never deletes), so the rows have to go here.
--
-- ATTENDANCE_ACCESS goes for the same reason: it named a "تسجيل الحضور" screen
-- that was never built.

-- Grants first, then the permissions they point at.
delete from user_permissions up
 using permissions p
 where p.id = up.permission_id
   and p.code in ('REPORT_VIEW', 'PAYMENT_VIEW', 'PAYMENT_MANAGE', 'WEBSITE_ACCESS',
                  'AI_ACCESS', 'AUTOMATION_ACCESS', 'MOBILE_APP_ACCESS', 'WHATSAPP_ACCESS',
                  'ATTENDANCE_ACCESS');

delete from permissions
 where code in ('REPORT_VIEW', 'PAYMENT_VIEW', 'PAYMENT_MANAGE', 'WEBSITE_ACCESS',
                'AI_ACCESS', 'AUTOMATION_ACCESS', 'MOBILE_APP_ACCESS', 'WHATSAPP_ACCESS',
                'ATTENDANCE_ACCESS');

-- Then the modules themselves. WHATSAPP goes because WhatsApp already has its
-- own per-admin switch in whatsapp_config, which is the one that actually works;
-- two switches for one feature is how a workspace ends up half-enabled.
delete from admin_modules am
 using modules m
 where m.id = am.module_id
   and m.code in ('REPORTS', 'PAYMENTS', 'WEBSITE', 'AI', 'AUTOMATION', 'MOBILE_APP', 'WHATSAPP');

delete from modules
 where code in ('REPORTS', 'PAYMENTS', 'WEBSITE', 'AI', 'AUTOMATION', 'MOBILE_APP', 'WHATSAPP');

-- Every surviving module becomes platform-controlled on the next boot, so the
-- super admin can switch any screen off per admin. Clearing the per-admin rows
-- makes them all fall back to default_enabled, which is the requested starting
-- point: every admin has the whole system until it is explicitly taken away.
-- Modules that were NOT platform-controlled before could otherwise carry a stale
-- disabled row and vanish from a workspace the moment the flag flipped.
delete from admin_modules;
