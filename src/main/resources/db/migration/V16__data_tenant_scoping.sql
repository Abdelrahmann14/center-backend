-- Multi-tenancy, part 2 of 2: admin_id on every data table.
--
-- After this migration every tenant-scoped row carries the owning Admin's id.
-- Hibernate's @TenantId then appends `admin_id = ?` to every select/update/
-- delete and fills it on insert, so an Admin can never read or touch another
-- Admin's data. Global uniqueness (center/grade name, group slot) also becomes
-- per-Admin, so two teachers may each have a "Main" center or a Saturday 4pm
-- group without colliding.
--
-- work_sessions and users are intentionally NOT given @TenantId (they are
-- written during login, before a tenant is known); they are scoped by explicit
-- admin_id filters in their queries instead.

do $$
declare
  the_admin uuid;
begin
  -- Exactly one admin was asserted by V15, so this is unambiguous.
  select id into the_admin from users where role = 'admin' limit 1;

  -- Add the column and backfill to the sole admin; NOT NULL/FK/index are set
  -- after this block. One block keeps the admin id in scope for all eight.

  -- students
  alter table students add column if not exists admin_id uuid;
  update students set admin_id = the_admin where admin_id is null;

  -- groups
  alter table groups add column if not exists admin_id uuid;
  update groups set admin_id = the_admin where admin_id is null;

  -- centers
  alter table centers add column if not exists admin_id uuid;
  update centers set admin_id = the_admin where admin_id is null;

  -- grades
  alter table grades add column if not exists admin_id uuid;
  update grades set admin_id = the_admin where admin_id is null;

  -- lectures
  alter table lectures add column if not exists admin_id uuid;
  update lectures set admin_id = the_admin where admin_id is null;

  -- registrations
  alter table registrations add column if not exists admin_id uuid;
  update registrations set admin_id = the_admin where admin_id is null;

  -- attendance
  alter table attendance add column if not exists admin_id uuid;
  update attendance set admin_id = the_admin where admin_id is null;

  -- center_grades
  alter table center_grades add column if not exists admin_id uuid;
  update center_grades set admin_id = the_admin where admin_id is null;
end $$;

-- Lock non-null + FK + index, one statement group per table.
alter table students      alter column admin_id set not null;
alter table groups        alter column admin_id set not null;
alter table centers       alter column admin_id set not null;
alter table grades        alter column admin_id set not null;
alter table lectures      alter column admin_id set not null;
alter table registrations alter column admin_id set not null;
alter table attendance    alter column admin_id set not null;
alter table center_grades alter column admin_id set not null;

alter table students      add constraint students_admin_fk      foreign key (admin_id) references users (id);
alter table groups        add constraint groups_admin_fk        foreign key (admin_id) references users (id);
alter table centers       add constraint centers_admin_fk       foreign key (admin_id) references users (id);
alter table grades        add constraint grades_admin_fk        foreign key (admin_id) references users (id);
alter table lectures      add constraint lectures_admin_fk      foreign key (admin_id) references users (id);
alter table registrations add constraint registrations_admin_fk foreign key (admin_id) references users (id);
alter table attendance    add constraint attendance_admin_fk    foreign key (admin_id) references users (id);
alter table center_grades add constraint center_grades_admin_fk foreign key (admin_id) references users (id);

create index if not exists students_admin_idx      on students (admin_id);
create index if not exists groups_admin_idx        on groups (admin_id);
create index if not exists centers_admin_idx       on centers (admin_id);
create index if not exists grades_admin_idx        on grades (admin_id);
create index if not exists lectures_admin_idx      on lectures (admin_id);
create index if not exists registrations_admin_idx on registrations (admin_id);
create index if not exists attendance_admin_idx    on attendance (admin_id);
create index if not exists center_grades_admin_idx on center_grades (admin_id);

-- Uniqueness that used to be global is now per-Admin.
alter table centers drop constraint if exists centers_name_key;
alter table centers add constraint centers_admin_name_key unique (admin_id, name);

alter table grades drop constraint if exists grades_name_key;
alter table grades add constraint grades_admin_name_key unique (admin_id, name);

alter table groups drop constraint if exists groups_day_of_week_start_time_key;
alter table groups add constraint groups_admin_day_time_key
  unique (admin_id, day_of_week, start_time);

-- registrations(lecture_id, student_id) stays as-is: a lecture already belongs
-- to exactly one Admin, so the pair can never span tenants.
