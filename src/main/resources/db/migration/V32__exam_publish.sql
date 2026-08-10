-- Exam publishing to students: a per-scheduled-exam password (fixed once set), a
-- published marker, and per-student attempt + answer storage for grading/review.
-- Attempt tables are sync-ready (client-settable uuid pk, version, deleted_at) for
-- the offline follow-up; the unique (admin, exam, student) makes submission
-- idempotent so an attempt can never be duplicated.
alter table exams
  add column if not exists exam_password text,
  add column if not exists published_at  timestamptz;

create table if not exists exam_attempts (
  id           uuid primary key default gen_random_uuid(),
  admin_id     uuid not null references users (id),
  exam_id      uuid not null references exams (id) on delete cascade,
  student_id   uuid not null references students (id) on delete cascade,
  started_at   timestamptz,
  submitted_at timestamptz,
  -- Snapshot of the achieved regular score, the bonus score, and the max at grading.
  score        numeric(6, 2),
  bonus_score  numeric(6, 2),
  max_score    numeric(6, 2),
  status       text not null default 'in_progress',
  deleted_at   timestamptz,
  created_at   timestamptz not null,
  created_by   text,
  updated_at   timestamptz not null,
  updated_by   text,
  version      bigint not null default 0,
  constraint exam_attempts_uidx unique (admin_id, exam_id, student_id)
);
create index if not exists exam_attempts_exam_idx on exam_attempts (exam_id);
create index if not exists exam_attempts_student_idx on exam_attempts (student_id);

create table if not exists exam_answers (
  id          uuid primary key default gen_random_uuid(),
  admin_id    uuid not null references users (id),
  attempt_id  uuid not null references exam_attempts (id) on delete cascade,
  question_id uuid not null references exam_questions (id) on delete cascade,
  -- The choices the student selected (one for single-answer, many for multi).
  choice_ids  uuid[] not null default '{}',
  correct     boolean not null default false,
  awarded     numeric(6, 2) not null default 0,
  created_at  timestamptz not null,
  created_by  text,
  updated_at  timestamptz not null,
  updated_by  text,
  version     bigint not null default 0,
  constraint exam_answers_uidx unique (attempt_id, question_id)
);
create index if not exists exam_answers_attempt_idx on exam_answers (attempt_id);
