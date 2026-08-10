-- Parent accounts, parent<->student links, and a global notifications inbox.
--
-- None of these are @TenantId tables. A parent can link to students in several
-- workspaces, so its records cannot belong to one; each row is instead pinned to
-- the exact parent/student/user it concerns. The link and notification tables are
-- read and written across workspaces, using native / explicit-id queries, just
-- like users and student_verification_codes.

-- Parent Code: one global sequence, mirroring students_serial_seq.
create sequence if not exists parents_serial_seq;

create table if not exists parents (
  id         uuid primary key default gen_random_uuid(),
  serial     integer not null unique default nextval('parents_serial_seq'),
  name       text not null,
  phone      text not null,
  -- The login account. Deleting the account deletes the parent profile.
  user_id    uuid not null unique references users (id) on delete cascade,
  created_at timestamptz not null default now(),
  created_by text,
  updated_at timestamptz not null default now(),
  updated_by text,
  version    bigint not null default 0
);
create index if not exists parents_user_id_idx on parents (user_id);

-- One row per (parent, student) linking attempt. Its status drives both the
-- student's pending-requests screen and the two sides' linked lists.
create table if not exists parent_student_links (
  id               uuid primary key default gen_random_uuid(),
  parent_id        uuid not null references parents (id) on delete cascade,
  student_id       uuid not null references students (id) on delete cascade,
  -- The workspace that owns the student, so a cross-tenant write (syncing the
  -- parent phone onto the student) can rebind to it without a lookup.
  student_admin_id uuid not null references users (id),
  status           text not null default 'pending'
                     check (status in ('pending', 'approved', 'rejected')),
  -- The phone the parent gave at request time - becomes the student's trusted
  -- parent phone on approval.
  phone_at_request text not null,
  decided_at       timestamptz,
  created_at       timestamptz not null default now(),
  created_by       text,
  updated_at       timestamptz not null default now(),
  updated_by       text,
  version          bigint not null default 0,
  unique (parent_id, student_id)
);
create index if not exists parent_student_links_student_idx
  on parent_student_links (student_id, status);
create index if not exists parent_student_links_parent_idx
  on parent_student_links (parent_id, status);

-- Short-lived WhatsApp codes for the parent forgot-password flow. Mirrors
-- student_verification_codes but keyed to a parent, not a student.
create table if not exists parent_verification_codes (
  id         uuid primary key default gen_random_uuid(),
  parent_id  uuid not null references parents (id) on delete cascade,
  code       text not null,
  expires_at timestamptz not null,
  attempts   int not null default 0,
  consumed   boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists parent_verification_codes_parent_idx
  on parent_verification_codes (parent_id, created_at desc);

-- Global in-app inbox, available to every role. Not tenant-scoped: a parent's
-- notifications span workspaces, and each row is pinned to one recipient account.
create table if not exists notifications (
  id                uuid primary key default gen_random_uuid(),
  recipient_user_id uuid not null references users (id) on delete cascade,
  type              text not null,
  title             text not null,
  body              text not null,
  -- Optional deep-link target (e.g. the parent_student_links row to act on).
  link_id           uuid,
  is_read           boolean not null default false,
  created_at        timestamptz not null default now()
);
create index if not exists notifications_recipient_idx
  on notifications (recipient_user_id, created_at desc);
