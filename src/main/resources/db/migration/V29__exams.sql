-- Lesson Exams: an admin-authored MCQ exam linked to a lesson, its questions and
-- choices, and its schedule (date + groups). Tenant-scoped like the rest of the
-- data. Designed sync-ready (client-settable UUID pk, version, deleted_at) for
-- the offline follow-up, though wired online-first for now.

create table if not exists exams (
  id               uuid primary key default gen_random_uuid(),
  admin_id         uuid not null references users (id),
  lecture_id       uuid not null references lectures (id) on delete cascade,
  name             text not null,
  grade            text,
  max_score        numeric(6, 2),
  duration_minutes int not null default 30,
  scheduled_date   date,
  -- Groups assigned when the exam is scheduled. Array, mirroring students.*_phones.
  group_ids        uuid[] not null default '{}',
  deleted_at       timestamptz,
  created_at       timestamptz not null,
  created_by       text,
  updated_at       timestamptz not null,
  updated_by       text,
  version          bigint not null default 0
);
create index if not exists exams_admin_grade_idx on exams (admin_id, grade);
create index if not exists exams_lecture_idx on exams (lecture_id);

create table if not exists exam_questions (
  id         uuid primary key default gen_random_uuid(),
  admin_id   uuid not null references users (id),
  exam_id    uuid not null references exams (id) on delete cascade,
  text       text not null,
  position   int not null default 0,
  created_at timestamptz not null,
  created_by text,
  updated_at timestamptz not null,
  updated_by text,
  version    bigint not null default 0
);
create index if not exists exam_questions_exam_idx on exam_questions (exam_id, position);

create table if not exists exam_choices (
  id          uuid primary key default gen_random_uuid(),
  admin_id    uuid not null references users (id),
  question_id uuid not null references exam_questions (id) on delete cascade,
  -- Fully custom label: "A"/"B" or "أ"/"ب" or anything the admin types.
  label       text not null,
  text        text not null,
  is_correct  boolean not null default false,
  position    int not null default 0,
  created_at  timestamptz not null,
  created_by  text,
  updated_at  timestamptz not null,
  updated_by  text,
  version     bigint not null default 0
);
create index if not exists exam_choices_question_idx on exam_choices (question_id, position);
