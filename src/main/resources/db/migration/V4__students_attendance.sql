-- Phase 2: student tracking foundation + drop lesson pricing.
-- Students belong to a group (سنتر/مجموعة); attendance logs each time a
-- student attended a group. Group cards read student_count + last attendance.

create table if not exists students (
  id         uuid primary key default gen_random_uuid(),
  name       text not null,
  group_id   uuid references groups(id) on delete set null,
  is_active  boolean not null default true,
  created_at timestamptz not null default now()
);

create index if not exists students_group_id_idx on students (group_id);

create table if not exists attendance (
  id          uuid primary key default gen_random_uuid(),
  group_id    uuid not null references groups(id) on delete cascade,
  student_id  uuid not null references students(id) on delete cascade,
  attended_on date not null default current_date,
  created_at  timestamptz not null default now()
);

create index if not exists attendance_group_id_idx on attendance (group_id);

-- Prices removed system-wide.
alter table groups drop column if exists lesson_price;
