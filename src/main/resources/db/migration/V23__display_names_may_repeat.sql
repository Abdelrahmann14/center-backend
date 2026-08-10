-- The username is only a DISPLAY NAME now (V21 moved authentication to email),
-- and two students may legitimately share the same full Arabic name. Drop the
-- global uniqueness that V19 added; identity lives in users.email, which keeps
-- its own unique index.
alter table users drop constraint if exists users_username_key;
