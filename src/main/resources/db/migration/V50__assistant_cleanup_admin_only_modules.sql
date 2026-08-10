-- 1. Assistant payroll and work-attendance are gone from the product.
--    work_sessions existed only to count an assistant's attended days, and
--    daily_rate/notes only to derive and annotate their salary.
drop table if exists work_sessions;

alter table users drop column if exists daily_rate;
alter table users drop column if exists notes;

-- 2. An assistant's contact number, kept for later use (never a login).
alter table users add column if not exists phone varchar(20);

-- 3. Four areas are the admin's alone and can no longer be delegated:
--    الإحصائيات · المجموعات والسناتر · المساعدون · تكامل الخدمات.
--    Dropping their permissions revokes every existing grant (user_permissions
--    cascades on permission_id) and removes them from the grant screen; their
--    controllers now check the admin role instead.
delete from user_permissions
where permission_id in (
  select id from permissions
  where code in ('ANALYTICS_VIEW',
                 'GROUP_CREATE', 'GROUP_UPDATE', 'GROUP_DELETE',
                 'STAGE_CREATE', 'STAGE_UPDATE', 'STAGE_DELETE',
                 'CENTER_CREATE', 'CENTER_UPDATE', 'CENTER_DELETE',
                 'USER_CREATE', 'USER_UPDATE', 'USER_DELETE', 'USER_PERMISSIONS')
);

delete from permissions
where code in ('ANALYTICS_VIEW',
               'GROUP_CREATE', 'GROUP_UPDATE', 'GROUP_DELETE',
               'STAGE_CREATE', 'STAGE_UPDATE', 'STAGE_DELETE',
               'CENTER_CREATE', 'CENTER_UPDATE', 'CENTER_DELETE',
               'USER_CREATE', 'USER_UPDATE', 'USER_DELETE', 'USER_PERMISSIONS');

-- The three workspace modules that are now empty disappear with their rows; the
-- super admin's per-teacher toggles (admin_modules) reference module ids, so
-- clear those first. ANALYTICS stays a module: the super admin still switches
-- the dashboard on or off per teacher, it just cannot be granted onwards.
delete from admin_modules
where module_id in (select id from modules where code in ('GROUPS', 'STAGES', 'CENTERS', 'TEAM'));

delete from modules where code in ('GROUPS', 'STAGES', 'CENTERS', 'TEAM');

update modules set admin_managed = false where code = 'ANALYTICS';

-- 4. The student analytics screen and its WhatsApp report are separate
--    capabilities now, so the admin can hand out the record without also
--    handing out the ability to message parents. Every assistant who could
--    already open a student keeps the read side; sending stays with the admin
--    until it is granted explicitly.
insert into permissions (id, module_id, code, action, name_ar, sort_order, created_at, updated_at)
select gen_random_uuid(), m.id, v.code, v.action, v.name_ar, v.sort_order, now(), now()
from (values
  ('STUDENT_ANALYTICS',   'VIEW', 'عرض تحليلات الطالب', 4),
  ('STUDENT_REPORT_SEND', 'SEND', 'إرسال تقرير الطالب', 5)
) as v(code, action, name_ar, sort_order)
join modules m on m.code = 'STUDENTS'
on conflict (code) do nothing;

insert into user_permissions (id, user_id, admin_id, permission_id, created_at, updated_at)
select gen_random_uuid(), up.user_id, up.admin_id, target.id, now(), now()
from user_permissions up
join permissions src on src.id = up.permission_id and src.code = 'STUDENT_VIEW'
join permissions target on target.code = 'STUDENT_ANALYTICS'
on conflict (user_id, permission_id) do nothing;
