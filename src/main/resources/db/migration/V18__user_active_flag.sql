-- Accounts can be deactivated without deletion.
--
-- A deactivated user cannot sign in; deactivating an Admin also locks its whole
-- workspace out (its assistants are refused at login because their owning Admin
-- is inactive). Additive: every existing account stays active.

alter table users
  add column if not exists is_active boolean not null default true;
