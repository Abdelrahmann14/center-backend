-- New user role: parent (guardian).
--
-- A parent is a peer of student in the role set. Unlike a student it may link to
-- students across different workspaces, so - like an admin/super_admin root - it
-- owns no workspace and its users.admin_id stays NULL. It is created inactive and
-- only enabled once a student approves the link (see V25).
--
-- Additive: only widens the check constraint; no existing row changes.

alter table users drop constraint if exists users_role_check;
alter table users
  add constraint users_role_check
  check (role in ('super_admin', 'admin', 'user', 'student', 'parent'));
