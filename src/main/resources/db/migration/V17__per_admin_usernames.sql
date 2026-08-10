-- Usernames become unique per workspace instead of globally.
--
-- Two Admins may each have an assistant with the same username; only the row
-- id stays globally unique. Authentication resolves the workspace first, then
-- matches the username within it, so duplicates across workspaces are never
-- ambiguous.
--
-- Data-safe: usernames were globally unique until now, so no existing row can
-- collide under the narrower per-workspace rule. Nothing is deleted or rewritten
-- - only the constraints change.

-- Drop the old global unique on users.username (named users_username_key by V1).
alter table users drop constraint if exists users_username_key;

-- Roots (admin + super_admin) have no owning admin; their usernames stay unique
-- among themselves so a workspace can be resolved by an Admin's username.
create unique index if not exists users_root_username_uq
  on users (username)
  where admin_id is null;

-- Assistants (and future students) are unique only within their own Admin.
create unique index if not exists users_admin_username_uq
  on users (admin_id, username)
  where admin_id is not null;
