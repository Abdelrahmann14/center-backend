-- Put centers on the change feed, and seed both centers and groups so a client
-- syncing from cursor 0 receives the whole picture.
--
-- A center's price list (center_grades) is NOT a feed entity of its own. Its
-- primary key is (center_id, grade) - there is no row UUID for the protocol to
-- address, and the price list is already saved as one unit with its center by
-- both the REST API and the service. So the center travels with its grades
-- embedded, and a price edit is logged by touching the parent center below.

drop trigger if exists centers_sync_log on centers;
create trigger centers_sync_log
  after insert or update or delete on centers
  for each row execute function sync_log_change('center');

-- A price row changing is a change to its center as far as any client is
-- concerned, so it is logged against the center rather than on its own.
create or replace function sync_log_center_grade() returns trigger as $$
declare
  target uuid;
  owner  uuid;
begin
  if tg_op = 'DELETE' then
    target := old.center_id;
    owner  := old.admin_id;
  else
    target := new.center_id;
    owner  := new.admin_id;
  end if;
  insert into sync_change_log (admin_id, entity, row_id, op)
  values (owner, 'center', target, 'upsert');
  return null;
end;
$$ language plpgsql;

drop trigger if exists center_grades_sync_log on center_grades;
create trigger center_grades_sync_log
  after insert or update or delete on center_grades
  for each row execute function sync_log_center_grade();

-- Seed. Centers have never been on the feed at all, and groups were seeded once
-- by V28 - but only for rows that existed then. A no-op update fires the trigger
-- per row without changing a value.
update centers set updated_at = updated_at;
update groups  set updated_at = updated_at;
