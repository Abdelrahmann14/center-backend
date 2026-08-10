-- Lectures (الحصص): a session record with optional exam + homework + attendees.

create table if not exists lectures (
  id                   uuid primary key default gen_random_uuid(),
  name                 text not null,
  grade                text,
  exam_name            text,
  exam_grade           text,
  homework             text,
  notes                text,
  attending_assistants text[] not null default '{}',  -- assistant usernames
  created_at           timestamptz not null default now()
);
