-- Assistant attendance on a lesson invoice.
--
-- One row = one assistant marked present at one session. The session is
-- (lecture, group, date), the same key finance_entries uses, so a lesson taught
-- to two groups keeps two attendance lists. Edited by replacing the whole set for
-- a session.
--
-- Not synced (no change-feed trigger): attendance is set from the Financials
-- screen, whose invoices are derived on the server and cannot be opened offline,
-- so there is nothing for an offline desk to queue here.
create table if not exists lesson_attendances (
  id           uuid primary key,
  admin_id     uuid not null,
  lecture_id   uuid not null references lectures (id) on delete cascade,
  group_id     uuid references groups (id) on delete cascade,
  session_date date not null,
  user_id      uuid not null references users (id) on delete cascade,
  created_at   timestamptz not null default now(),
  created_by   text,
  updated_at   timestamptz not null default now(),
  updated_by   text,
  version      bigint not null default 0
);

-- The page reads a whole date range at once, then buckets by session in memory.
create index if not exists lesson_attendances_session_idx
  on lesson_attendances (admin_id, session_date);

create index if not exists lesson_attendances_lecture_idx
  on lesson_attendances (admin_id, lecture_id, group_id);

-- One assistant appears at most once per session. group_id is nullable, so the
-- key coalesces it to a fixed sentinel - a plain multi-column unique index would
-- treat two null-group rows as distinct and let a duplicate through.
create unique index if not exists lesson_attendances_unique_idx
  on lesson_attendances (
    admin_id, lecture_id,
    coalesce(group_id, '00000000-0000-0000-0000-000000000000'::uuid),
    session_date, user_id);
