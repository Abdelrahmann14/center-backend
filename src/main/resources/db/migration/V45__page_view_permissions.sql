-- Page-access permissions for the operational screens, so an Admin can decide
-- per assistant which cards/pages each assistant sees. These are also registered
-- in code (ModuleCatalog); they are inserted here first so the grants below can
-- reference them in this same migration (the catalog sync runner, which upserts
-- by code on boot, then reconciles them idempotently).
insert into permissions (id, module_id, code, action, name_ar, sort_order, created_at, updated_at)
select gen_random_uuid(), m.id, v.code, v.action, v.name_ar, v.sort_order, now(), now()
from (values
  ('STUDENTS',      'STUDENT_VIEW',        'VIEW',   'عرض الطلاب',        0),
  ('LESSONS',       'LESSON_VIEW',         'VIEW',   'عرض الحصص',         0),
  ('REGISTRATIONS', 'REGISTRATION_ACCESS', 'ACCESS', 'صفحة تسجيل الحصة',  0),
  ('REGISTRATIONS', 'ATTENDANCE_ACCESS',   'ACCESS', 'صفحة تسجيل الحضور', 4)
) as v(module_code, code, action, name_ar, sort_order)
join modules m on m.code = v.module_code
on conflict (code) do nothing;

-- Preserve current behavior: existing assistants already see all four screens by
-- role, so grant them the new permissions. The admin can revoke per assistant.
insert into user_permissions (id, user_id, admin_id, permission_id, created_at, updated_at)
select gen_random_uuid(), u.id, u.admin_id, p.id, now(), now()
from users u
join permissions p on p.code in ('STUDENT_VIEW', 'LESSON_VIEW', 'REGISTRATION_ACCESS', 'ATTENDANCE_ACCESS')
where u.role = 'user' and u.admin_id is not null
on conflict (user_id, permission_id) do nothing;

-- Tenant-scope the messaging tables. A null admin_id means the global/super row
-- (existing broadcasts, the seeded system templates); a set admin_id is a row
-- owned by that admin's workspace.
alter table outgoing_messages add column if not exists admin_id uuid;
create index if not exists outgoing_messages_admin_idx on outgoing_messages (admin_id, created_at desc);

alter table message_templates add column if not exists admin_id uuid;
create index if not exists message_templates_admin_idx on message_templates (admin_id);
