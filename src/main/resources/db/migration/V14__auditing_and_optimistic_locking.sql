-- JPA auditing (@CreatedBy/@LastModifiedDate/@LastModifiedBy) + optimistic
-- locking (@Version) for the entities the app edits.
--
-- Additive only: every column is nullable or has a default, so existing rows
-- stay valid and the old data is never rewritten.
--
-- attendance / work_sessions / center_grades are intentionally excluded: they
-- are append-only or child rows, never concurrently edited through the UI.

alter table users
  add column if not exists created_by text,
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists updated_by text,
  add column if not exists version    bigint not null default 0;

alter table grades
  add column if not exists created_by text,
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists updated_by text,
  add column if not exists version    bigint not null default 0;

alter table centers
  add column if not exists created_by text,
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists updated_by text,
  add column if not exists version    bigint not null default 0;

alter table groups
  add column if not exists created_by text,
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists updated_by text,
  add column if not exists version    bigint not null default 0;

alter table students
  add column if not exists created_by text,
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists updated_by text,
  add column if not exists version    bigint not null default 0;

alter table registrations
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists updated_by text,
  add column if not exists version    bigint not null default 0;

-- lectures already carries created_by / updated_at / updated_by (V11).
alter table lectures
  add column if not exists version bigint not null default 0;

-- Indexes behind the filters the list endpoints now expose.
create index if not exists students_grade_idx    on students (grade);
create index if not exists students_is_active_idx on students (is_active);
create index if not exists lectures_grade_idx    on lectures (grade);
create index if not exists registrations_group_idx on registrations (group_id);
