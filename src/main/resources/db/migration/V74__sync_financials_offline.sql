-- Put the Financials screen offline.
--
-- The invoices themselves are derived, never stored, so there is nothing to
-- mirror for them: a disconnected client rebuilds them from the registrations,
-- groups and manual lines it already holds. What it did NOT hold is the two
-- tables below, and without them the page could show the money but not who
-- worked the lesson - which is half of what the screen is for.
--
-- V66 said assistant attendance was deliberately not synced, on the grounds that
-- the invoices could not be opened offline. That premise is what this changes.

-- ── Assistant attendance ────────────────────────────────────────────────────
drop trigger if exists lesson_attendances_sync_log on lesson_attendances;
create trigger lesson_attendances_sync_log
  after insert or update or delete on lesson_attendances
  for each row execute function sync_log_change('lesson_attendance');

-- ── The assistants themselves ───────────────────────────────────────────────
-- The attendance form is a list of names with a tick each, so the names have to
-- be mirrored too. Only assistants (role 'user') go on the feed: the workspace's
-- own admin row, its parents and its students are either not tickable or not the
-- teacher's staff, and a feed carrying every user of every kind would ship
-- password hashes' neighbours for no reader.
--
-- Split in two because a WHEN clause may only read NEW on insert and OLD on
-- delete. The update trigger fires on either side of the role, so an assistant
-- demoted to another role still logs a change - the pull resolver then fails to
-- resolve the row and the feed reports it as a deletion, which is what a client
-- mirroring only assistants needs to hear.
drop trigger if exists users_assistant_sync_log_ins on users;
create trigger users_assistant_sync_log_ins
  after insert on users
  for each row when (new.role = 'user')
  execute function sync_log_change('assistant');

drop trigger if exists users_assistant_sync_log_upd on users;
create trigger users_assistant_sync_log_upd
  after update on users
  for each row when (new.role = 'user' or old.role = 'user')
  execute function sync_log_change('assistant');

drop trigger if exists users_assistant_sync_log_del on users;
create trigger users_assistant_sync_log_del
  after delete on users
  for each row when (old.role = 'user')
  execute function sync_log_change('assistant');

-- Seed both, so a client syncing from cursor 0 gets the whole history rather
-- than only what changes after this migration. A no-op update fires the
-- triggers above without rewriting any value.
update lesson_attendances set updated_at = updated_at;
update users set updated_at = updated_at where role = 'user';
