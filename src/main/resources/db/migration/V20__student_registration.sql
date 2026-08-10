-- Student self-registration.
--
-- Students are the only users who create their own accounts. A registered
-- student gets a users row (role 'student', admin_id = their teacher) linked to
-- their students record, so their educational data and their login are one
-- identity. Existing students claim their record by verifying a code sent to
-- the phone already stored for them.
--
-- Additive: existing rows are untouched (user_id stays NULL until claimed).

alter table students
  add column if not exists user_id uuid unique references users (id) on delete set null;

create index if not exists students_user_id_idx on students (user_id);

-- Short-lived WhatsApp verification codes. Not tenant-scoped: they are used
-- before authentication, and each row is already pinned to one student.
create table if not exists student_verification_codes (
  id         uuid primary key default gen_random_uuid(),
  student_id uuid not null references students (id) on delete cascade,
  code       text not null,
  expires_at timestamptz not null,
  attempts   int not null default 0,
  consumed   boolean not null default false,
  created_at timestamptz not null default now()
);

-- Latest-code lookups and the per-student send-rate check.
create index if not exists student_verification_codes_student_idx
  on student_verification_codes (student_id, created_at desc);
