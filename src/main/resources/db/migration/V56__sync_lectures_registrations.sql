-- Put lessons and registrations on the change feed.
--
-- Registrations are the largest table here and the one the daily desk work
-- runs through, so they are also the reason the feed needs to stay cheap: the
-- trigger writes one small row per change, never the payload.

drop trigger if exists lectures_sync_log on lectures;
create trigger lectures_sync_log
  after insert or update or delete on lectures
  for each row execute function sync_log_change('lecture');

drop trigger if exists registrations_sync_log on registrations;
create trigger registrations_sync_log
  after insert or update or delete on registrations
  for each row execute function sync_log_change('registration');

-- Registrations are read by lesson and by student on every screen that matters
-- offline; without these a client-side filter walks the whole table.
create index if not exists registrations_admin_lecture_idx
  on registrations (admin_id, lecture_id);
create index if not exists registrations_admin_student_idx
  on registrations (admin_id, student_id);

-- Seed both so a client syncing from cursor 0 gets the whole history rather
-- than only what has changed since this migration.
update lectures      set updated_at = updated_at;
update registrations set updated_at = updated_at;
