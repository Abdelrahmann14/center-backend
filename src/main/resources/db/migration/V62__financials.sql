-- Financials: what the center takes, and the manual lines a lesson invoice carries.
--
-- The cut is configured per (center, grade) rather than per center: one center
-- routinely charges a different share for a third-secondary group than for a
-- first-preparatory one, and every group of that grade at that center inherits
-- it. center_grades already keys on exactly that pair, so the share rides on the
-- price row instead of opening a table of its own.
alter table center_grades
  add column if not exists percentage numeric(5, 2) not null default 0;

-- A manual income or expense line attached to one lesson session. The session is
-- (lecture, group, date): the same lesson taught to two groups keeps two
-- invoices, and each one carries its own lines.
create table if not exists finance_entries (
  id           uuid primary key,
  admin_id     uuid not null,
  lecture_id   uuid not null references lectures (id) on delete cascade,
  group_id     uuid references groups (id) on delete cascade,
  session_date date not null,
  kind         text not null check (kind in ('income', 'expense')),
  description  text not null,
  amount       numeric(12, 2) not null check (amount >= 0),
  created_at   timestamptz not null default now(),
  created_by   text,
  updated_at   timestamptz not null default now(),
  updated_by   text,
  version      bigint not null default 0
);

-- The page reads a whole date range at once, then buckets by session in memory.
create index if not exists finance_entries_session_idx
  on finance_entries (admin_id, session_date);

create index if not exists finance_entries_lecture_idx
  on finance_entries (admin_id, lecture_id, group_id);

-- Invoice lines are hand-written, so they are exactly the kind of row a desk
-- with no connection produces. Log them to the change feed like every other
-- syncable entity.
drop trigger if exists finance_entries_sync_log on finance_entries;
create trigger finance_entries_sync_log
  after insert or update or delete on finance_entries
  for each row execute function sync_log_change('finance_entry');
