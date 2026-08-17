-- A center price edit must refresh the groups that quote it.
--
-- The pulled group row now carries lesson_price - the center's rate for that
-- group's grade - because the student form uses it as the default price and as
-- the ceiling a discount is measured against, and a client with no connection
-- has nowhere else to read it from.
--
-- That makes a group row depend on a table it has no foreign key to: groups
-- match a center by NAME, and center_grades holds the price. V55 already logged
-- a price edit against its parent center; without the second insert below the
-- group rows would keep quoting the old rate until each one happened to be
-- edited, which is the same stale-mirror trap V57 and V58 had to undo.
create or replace function sync_log_center_grade() returns trigger as $$
declare
  target uuid;
  owner  uuid;
  centre text;
  lvl    text;
begin
  if tg_op = 'DELETE' then
    target := old.center_id;
    owner  := old.admin_id;
    lvl    := old.grade;
  else
    target := new.center_id;
    owner  := new.admin_id;
    lvl    := new.grade;
  end if;

  insert into sync_change_log (admin_id, entity, row_id, op)
  values (owner, 'center', target, 'upsert');

  select name into centre from centers where id = target;
  if centre is not null then
    insert into sync_change_log (admin_id, entity, row_id, op)
    select owner, 'group', g.id, 'upsert'
      from groups g
     where g.admin_id = owner and g.center_name = centre and g.grade = lvl;
  end if;

  return null;
end;
$$ language plpgsql;
