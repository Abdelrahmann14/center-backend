-- Lesson registration (attendance of a student in a lecture/lesson).

create table if not exists registrations (
  id            uuid primary key default gen_random_uuid(),
  lecture_id    uuid not null references lectures(id) on delete cascade,
  student_id    uuid not null references students(id) on delete cascade,
  group_id      uuid references groups(id) on delete set null,  -- group registered under
  status        text not null default 'present'
                  check (status in ('present', 'absent', 'removed')),
  exam_score    numeric(6, 2),        -- null = not examined
  registered_by text,
  created_at    timestamptz not null default now(),
  unique (lecture_id, student_id)     -- one record per student per lesson
);

create index if not exists registrations_lecture_idx on registrations (lecture_id);
create index if not exists registrations_student_idx on registrations (student_id);
