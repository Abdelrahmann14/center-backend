-- Usernames become GLOBALLY unique again (the single login identifier,
-- Instagram-style). This reverses V17's per-workspace scheme. Ownership is
-- unaffected - it lives in users.admin_id, independent of the username.
--
-- Data-safe: if any username is currently duplicated (V17 allowed the same
-- assistant name under different Admins) the migration aborts with the list, so
-- nothing is lost - rename the clashes, then re-run.

do $$
declare
  dupes text;
begin
  select string_agg(username, ', ') into dupes
  from (
    select username from users group by username having count(*) > 1
  ) d;

  if dupes is not null then
    raise exception
      'Cannot enforce global unique usernames - duplicates exist: %. Rename them first.', dupes;
  end if;
end $$;

-- Drop V17's per-workspace partial indexes.
drop index if exists users_root_username_uq;
drop index if exists users_admin_username_uq;

-- One global unique constraint on username.
alter table users add constraint users_username_key unique (username);
