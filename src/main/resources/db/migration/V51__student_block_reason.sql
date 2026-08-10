-- Blocking a student is now an explicit, explained decision rather than a bare
-- inactive flag: is_active = false means blocked, and this carries the why so
-- whoever looks at the record later knows. Null while the student is active.
alter table students add column if not exists block_reason varchar(500);
