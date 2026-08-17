-- Groundwork for offline-first writes. Three independent changes:
--   1. student codes become per-workspace, so a client can mint the next one
--   2. a WhatsApp-presence cache, so a number is verified once and not re-asked
--   3. the change feed learns to report deletions

-- ── 1. Per-workspace student codes ──────────────────────────────────────────
-- students.serial was one GLOBAL sequence with a globally unique index, so two
-- workspaces interleaved their codes (admin A: 1, 5, 9). Offline the client has
-- only its own workspace mirrored, so it can only ever compute max+1 WITHIN a
-- workspace - which two workspaces would both answer with the same number.
-- Scoping the uniqueness to the workspace makes that answer correct.
--
-- Existing codes are NOT renumbered: they are already unique per workspace (the
-- old index was globally unique, which implies it), so this widens what is
-- allowed and rewrites nothing.
drop index if exists students_serial_uidx;
create unique index if not exists students_admin_serial_uidx on students (admin_id, serial);

-- The sequence default has to go, or serial is never null and the trigger below
-- never gets to run. students_serial_seq is left in place but unused; dropping
-- it would break a restore of any dump taken before this migration.
alter table students alter column serial drop default;

-- Assign the next code within the workspace, but only when the caller did not
-- bring its own - an offline client sends the code it already showed the user,
-- and that code must survive the sync.
--
-- The advisory lock serialises inserts per workspace for the rest of the
-- transaction. Without it two concurrent inserts both read the same max and the
-- unique index above rejects one of them; the lock is far cheaper than that
-- retry, and it is taken on the workspace id so unrelated workspaces never wait
-- on each other.
create or replace function assign_student_serial() returns trigger as $$
begin
  if new.serial is null then
    perform pg_advisory_xact_lock(hashtextextended(new.admin_id::text, 0));
    select coalesce(max(serial), 0) + 1 into new.serial
      from students where admin_id = new.admin_id;
  end if;
  return new;
end;
$$ language plpgsql;

drop trigger if exists students_assign_serial on students;
create trigger students_assign_serial
  before insert on students
  for each row execute function assign_student_serial();

-- ── 2. WhatsApp presence cache ──────────────────────────────────────────────
-- Whether a number is on WhatsApp is a fact about the number, not about the
-- student, and it does not change. Storing it once stops every student form and
-- every re-edit from spending another Green API round trip, and it is what lets
-- the check run later, in the background, for numbers entered while offline.
--
-- Keyed per workspace because the lookup is answered by that workspace's own
-- Green API instance (whatsapp_instance.owner_admin_id).
create table if not exists whatsapp_numbers (
  id            uuid primary key default gen_random_uuid(),
  admin_id      uuid not null references users (id),
  phone         text not null,
  -- null = queued, never successfully checked yet. false = checked, not on
  -- WhatsApp. Only a real answer from Green API ever writes this column.
  has_whatsapp  boolean,
  checked_at    timestamptz,
  -- Why the last attempt failed, for the retry job. Cleared on success.
  last_error    text,
  attempts      integer not null default 0,
  created_at    timestamptz not null default now()
);

create unique index if not exists whatsapp_numbers_key
  on whatsapp_numbers (admin_id, phone);

-- The background re-check scans for rows still awaiting an answer.
create index if not exists whatsapp_numbers_pending_idx
  on whatsapp_numbers (admin_id) where has_whatsapp is null;

-- ── 3. Deletions in the change feed ─────────────────────────────────────────
-- sync_change_log.op already allows 'delete' and has since V27, but no trigger
-- ever wrote one: every trigger was AFTER INSERT OR UPDATE only. A row deleted
-- on the server therefore appended nothing to the feed and stayed forever in
-- every offline client's mirror.
--
-- On DELETE the row is in OLD, not NEW - reading new.admin_id there would raise.
create or replace function sync_log_change() returns trigger as $$
begin
  if tg_op = 'DELETE' then
    insert into sync_change_log (admin_id, entity, row_id, op)
    values (old.admin_id, tg_argv[0], old.id, 'delete');
    return old;
  end if;
  insert into sync_change_log (admin_id, entity, row_id, op)
  values (new.admin_id, tg_argv[0], new.id, 'upsert');
  return new;
end;
$$ language plpgsql;

drop trigger if exists students_sync_log on students;
create trigger students_sync_log
  after insert or update or delete on students
  for each row execute function sync_log_change('student');

drop trigger if exists groups_sync_log on groups;
create trigger groups_sync_log
  after insert or update or delete on groups
  for each row execute function sync_log_change('group');

-- Attendance was insert-only in the feed; a removed mark has to travel too.
drop trigger if exists attendance_sync_log on attendance;
create trigger attendance_sync_log
  after insert or update or delete on attendance
  for each row execute function sync_log_change('attendance');
