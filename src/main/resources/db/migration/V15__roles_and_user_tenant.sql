-- Multi-tenancy, part 1 of 2: the role hierarchy and the user->admin link.
--
-- The system becomes multi-tenant with the Admin (teacher) as the root of each
-- isolated workspace. This migration widens the role set and links every
-- non-admin user to the Admin that owns them. Part 2 (V16) adds admin_id to the
-- data tables.
--
-- Safety: the backfill asserts there is EXACTLY ONE existing admin (the single
-- teacher the app was built for). If that is ever untrue the whole migration
-- aborts inside its transaction and nothing is changed.

-- 1) Widen the role check to the new four-role hierarchy.
--    admin/user already exist; user == assistant. super_admin == the developer,
--    student == the future mobile role.
alter table users drop constraint if exists users_role_check;
alter table users
  add constraint users_role_check
  check (role in ('super_admin', 'admin', 'user', 'student'));

-- 2) Each user (except an admin and the super_admin) belongs to one Admin.
--    An admin is the root of its own workspace, so its admin_id stays NULL.
alter table users add column if not exists admin_id uuid references users (id);

-- 3) Backfill: attach every existing assistant to the one existing admin.
do $$
declare
  admin_count int;
  the_admin   uuid;
begin
  -- uuid has no min()/max() aggregate, so count and id are fetched separately.
  select count(*) into admin_count from users where role = 'admin';

  if admin_count <> 1 then
    raise exception
      'Tenancy backfill expects exactly 1 admin, found %. Aborting.', admin_count;
  end if;

  select id into the_admin from users where role = 'admin';

  update users set admin_id = the_admin
    where role = 'user' and admin_id is null;
end $$;

create index if not exists users_admin_id_idx on users (admin_id);
