-- Hierarchical RBAC. Two authorization levels layered over the existing roles:
--   L1 (Super Admin): enable/disable whole platform modules per Admin.
--   L2 (Admin): grant fine-grained, module-grouped permissions to their users.
--
-- modules/permissions are a GLOBAL catalog (no admin_id, no @TenantId). The two
-- grant tables carry an explicit admin_id/user_id and are deliberately NOT
-- tenant-scoped: the super admin writes admin_modules while unbound, and both are
-- read during authentication BEFORE the tenant is bound - a @TenantId here would
-- resolve to NO_TENANT and silently drop every grant. Same rationale as users.
--
-- Inheritance is resolved at check time (see PermissionResolver), never by
-- cascading deletes, so disabling a module makes its permissions inert instantly
-- and re-enabling restores prior grants with no data loss.

-- ---------------------------------------------------------------------------
-- Catalog
-- ---------------------------------------------------------------------------
create table if not exists modules (
  id                  uuid primary key default gen_random_uuid(),
  code                text not null unique,
  name_ar             text not null,
  description_ar      text,
  category            text not null default 'general',
  -- Ownership config (the spec's four configurable behaviors):
  --   platform_controlled: Super Admin gates it per-Admin.
  --   admin_managed:        Admin may assign its permissions to users.
  --   default_enabled:      on by default for new admins; when
  --                         platform_controlled AND NOT default_enabled the
  --                         module is "disabled until explicitly enabled".
  platform_controlled boolean not null default false,
  admin_managed       boolean not null default true,
  default_enabled     boolean not null default true,
  sort_order          int not null default 0,
  is_active           boolean not null default true,
  created_at          timestamptz not null,
  created_by          text,
  updated_at          timestamptz not null,
  updated_by          text,
  version             bigint not null default 0
);

create table if not exists permissions (
  id         uuid primary key default gen_random_uuid(),
  module_id  uuid not null references modules (id) on delete cascade,
  code       text not null unique,
  action     text not null,
  name_ar    text not null,
  sort_order int not null default 0,
  created_at timestamptz not null,
  created_by text,
  updated_at timestamptz not null,
  updated_by text,
  version    bigint not null default 0
);
create index if not exists permissions_module_idx on permissions (module_id, sort_order);

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
-- L1: which platform modules an Admin has. Absent row => module.default_enabled.
create table if not exists admin_modules (
  id         uuid primary key default gen_random_uuid(),
  admin_id   uuid not null references users (id) on delete cascade,
  module_id  uuid not null references modules (id) on delete cascade,
  enabled    boolean not null default true,
  created_at timestamptz not null,
  created_by text,
  updated_at timestamptz not null,
  updated_by text,
  version    bigint not null default 0,
  constraint admin_modules_uidx unique (admin_id, module_id)
);
create index if not exists admin_modules_admin_idx on admin_modules (admin_id);

-- L2: which permissions a user holds. admin_id denormalized for fast, tenant-free
-- resolution at auth time and for scoping the admin's management queries.
create table if not exists user_permissions (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references users (id) on delete cascade,
  admin_id      uuid not null references users (id) on delete cascade,
  permission_id uuid not null references permissions (id) on delete cascade,
  granted_by    uuid references users (id),
  created_at    timestamptz not null,
  created_by    text,
  updated_at    timestamptz not null,
  updated_by    text,
  version       bigint not null default 0,
  constraint user_permissions_uidx unique (user_id, permission_id)
);
create index if not exists user_permissions_user_idx on user_permissions (user_id);
create index if not exists user_permissions_admin_idx on user_permissions (admin_id);

-- ---------------------------------------------------------------------------
-- Seed the initial catalog. The code-side ModuleCatalog + ModuleCatalogSyncRunner
-- keep this in sync and add anything registered later; this is just the baseline
-- so the backfill below has rows to reference.
-- ---------------------------------------------------------------------------
insert into modules
  (code, name_ar, description_ar, category, platform_controlled, admin_managed, default_enabled, sort_order, created_at, updated_at)
values
  -- Admin-managed domain modules: always available (not platform-gated).
  ('STUDENTS',      'الطلاب',           'إدارة بيانات الطلاب',            'workspace', false, true,  true,  10, now(), now()),
  ('GROUPS',        'الشعب',            'إدارة الشعب والمواعيد',          'workspace', false, true,  true,  20, now(), now()),
  ('LESSONS',       'الحصص',            'إنشاء وإدارة الحصص',            'workspace', false, true,  true,  30, now(), now()),
  ('REGISTRATIONS', 'التسجيل والحضور',  'تسجيل الطلاب في الحصص والحضور', 'workspace', false, true,  true,  40, now(), now()),
  ('STAGES',        'الصفوف',           'إدارة الصفوف الدراسية',         'workspace', false, true,  true,  50, now(), now()),
  ('CENTERS',       'السناتر',          'إدارة السناتر',                'workspace', false, true,  true,  60, now(), now()),
  ('EXAMS',         'الاختبارات',       'بناء ونشر اختبارات الحصص',      'workspace', false, true,  true,  70, now(), now()),
  ('TEAM',          'المساعدون',        'إدارة حسابات المساعدين وصلاحياتهم', 'workspace', false, true,  true,  80, now(), now()),
  -- Platform modules: gated by the super admin per admin.
  ('ANALYTICS',     'التحليلات',        'لوحة التحليلات والإحصاءات',     'platform',  true,  true,  true,  100, now(), now()),
  ('NOTIFICATIONS', 'الإشعارات',        'إرسال الإشعارات',              'platform',  true,  true,  true,  110, now(), now()),
  ('WHATSAPP',      'واتساب',           'تكامل واتساب والرسائل',         'platform',  true,  false, true,  120, now(), now()),
  ('MOBILE_APP',    'تطبيق الجوال',      'تطبيق الطلاب وأولياء الأمور',   'platform',  true,  false, true,  130, now(), now()),
  ('REPORTS',       'التقارير',         'التقارير التفصيلية',           'platform',  true,  true,  false, 140, now(), now()),
  ('PAYMENTS',      'المدفوعات',        'إدارة المدفوعات',              'platform',  true,  true,  false, 150, now(), now()),
  ('WEBSITE',       'الموقع الإلكتروني', 'الموقع العام',                'platform',  true,  false, false, 160, now(), now()),
  ('AI',            'مزايا الذكاء الاصطناعي', 'المزايا الذكية',          'platform',  true,  false, false, 170, now(), now()),
  ('AUTOMATION',    'الأتمتة',          'مسارات العمل الآلية',          'platform',  true,  false, false, 180, now(), now())
on conflict (code) do nothing;

insert into permissions (module_id, code, action, name_ar, sort_order, created_at, updated_at)
select m.id, v.code, v.action, v.name_ar, v.sort_order, now(), now()
from (values
  ('STUDENTS',      'STUDENT_CREATE',      'CREATE',      'إضافة طالب',        1),
  ('STUDENTS',      'STUDENT_UPDATE',      'UPDATE',      'تعديل طالب',        2),
  ('STUDENTS',      'STUDENT_DELETE',      'DELETE',      'حذف طالب',          3),
  ('GROUPS',        'GROUP_CREATE',        'CREATE',      'إضافة شعبة',        1),
  ('GROUPS',        'GROUP_UPDATE',        'UPDATE',      'تعديل شعبة',        2),
  ('GROUPS',        'GROUP_DELETE',        'DELETE',      'حذف شعبة',          3),
  ('LESSONS',       'LESSON_CREATE',       'CREATE',      'إضافة حصة',         1),
  ('LESSONS',       'LESSON_UPDATE',       'UPDATE',      'تعديل حصة',         2),
  ('LESSONS',       'LESSON_DELETE',       'DELETE',      'حذف حصة',           3),
  ('REGISTRATIONS', 'REGISTRATION_CREATE', 'CREATE',      'تسجيل طالب',        1),
  ('REGISTRATIONS', 'REGISTRATION_UPDATE', 'UPDATE',      'تعديل تسجيل',       2),
  ('REGISTRATIONS', 'REGISTRATION_DELETE', 'DELETE',      'حذف تسجيل',         3),
  ('STAGES',        'STAGE_CREATE',        'CREATE',      'إضافة صف',          1),
  ('STAGES',        'STAGE_UPDATE',        'UPDATE',      'تعديل صف',          2),
  ('STAGES',        'STAGE_DELETE',        'DELETE',      'حذف صف',            3),
  ('CENTERS',       'CENTER_CREATE',       'CREATE',      'إضافة سنتر',        1),
  ('CENTERS',       'CENTER_UPDATE',       'UPDATE',      'تعديل سنتر',        2),
  ('CENTERS',       'CENTER_DELETE',       'DELETE',      'حذف سنتر',          3),
  ('EXAMS',         'EXAM_CREATE',         'CREATE',      'إنشاء اختبار',      1),
  ('EXAMS',         'EXAM_UPDATE',         'UPDATE',      'تعديل اختبار',      2),
  ('EXAMS',         'EXAM_DELETE',         'DELETE',      'حذف اختبار',        3),
  ('EXAMS',         'EXAM_PUBLISH',        'PUBLISH',     'نشر اختبار',        4),
  ('TEAM',          'USER_CREATE',         'CREATE',      'إضافة مساعد',       1),
  ('TEAM',          'USER_UPDATE',         'UPDATE',      'تعديل مساعد',       2),
  ('TEAM',          'USER_DELETE',         'DELETE',      'حذف مساعد',         3),
  ('TEAM',          'USER_PERMISSIONS',    'PERMISSIONS', 'إدارة الصلاحيات',   4),
  ('ANALYTICS',     'ANALYTICS_VIEW',      'VIEW',        'عرض التحليلات',     1),
  ('NOTIFICATIONS', 'NOTIFICATION_SEND',   'SEND',        'إرسال إشعارات',     1),
  ('WHATSAPP',      'WHATSAPP_ACCESS',     'ACCESS',      'الوصول لواتساب',    1),
  ('MOBILE_APP',    'MOBILE_APP_ACCESS',   'ACCESS',      'الوصول للتطبيق',    1),
  ('REPORTS',       'REPORT_VIEW',         'VIEW',        'عرض التقارير',      1),
  ('PAYMENTS',      'PAYMENT_VIEW',        'VIEW',        'عرض المدفوعات',     1),
  ('PAYMENTS',      'PAYMENT_MANAGE',      'MANAGE',      'إدارة المدفوعات',   2),
  ('WEBSITE',       'WEBSITE_ACCESS',      'ACCESS',      'الوصول للموقع',     1),
  ('AI',            'AI_ACCESS',           'ACCESS',      'استخدام الذكاء الاصطناعي', 1),
  ('AUTOMATION',    'AUTOMATION_ACCESS',   'ACCESS',      'استخدام الأتمتة',   1)
) as v (module_code, code, action, name_ar, sort_order)
join modules m on m.code = v.module_code
on conflict (code) do nothing;

-- ---------------------------------------------------------------------------
-- Backfill: preserve every existing user's access exactly.
-- ---------------------------------------------------------------------------
-- Existing admins had every feature; enable all platform modules for them so
-- nothing they use disappears. New admins follow default_enabled (seeded on
-- creation by SuperAdminService).
insert into admin_modules (admin_id, module_id, enabled, created_at, updated_at)
select u.id, m.id, true, now(), now()
from users u
cross join modules m
where u.role = 'admin' and m.platform_controlled = true
on conflict (admin_id, module_id) do nothing;

-- Existing assistants can already mutate students/groups/lessons/registrations
-- today (those controllers are ungated). Grant exactly those permissions so the
-- switch to permission-based checks changes nothing for them. Admin-only features
-- (stages/centers/exams/team/analytics) are intentionally NOT granted.
insert into user_permissions (user_id, admin_id, permission_id, created_at, updated_at)
select u.id, u.admin_id, p.id, now(), now()
from users u
join permissions p on true
join modules m on m.id = p.module_id
where u.role = 'user'
  and u.admin_id is not null
  and m.code in ('STUDENTS', 'GROUPS', 'LESSONS', 'REGISTRATIONS')
on conflict (user_id, permission_id) do nothing;
