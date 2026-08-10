-- Add groups to the sync change feed so clients can show real group names in the
-- offline attendance screen (not just a group-id prefix). Read-only on clients;
-- reuses the sync_log_change() trigger function from V27.

drop trigger if exists groups_sync_log on groups;
create trigger groups_sync_log
  after insert or update on groups
  for each row execute function sync_log_change('group');

-- Seed the feed with existing groups so a first pull includes them (a no-op
-- update bumps updated_at and fires the trigger once per row).
update groups set updated_at = updated_at;
