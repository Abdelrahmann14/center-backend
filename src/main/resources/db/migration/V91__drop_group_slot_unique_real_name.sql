-- V90 was a no-op. It dropped `groups_admin_id_day_of_week_start_time_key` -
-- the name Postgres generates for an UNNAMED unique constraint - but V16 gave
-- this one an explicit name when it re-scoped it per workspace:
--
--     alter table groups add constraint groups_admin_day_time_key
--       unique (admin_id, day_of_week, start_time);
--
-- So `drop constraint if exists` matched nothing, said nothing, and the rule
-- stayed. Worse than before the attempt: the friendly pre-check in
-- GroupServiceImpl had already gone, so a clash stopped being "يوجد مجموعة أخرى
-- في نفس اليوم والوقت" and became a raw integrity violation surfacing as
-- "حدث خطأ غير متوقع".
--
-- The real name, plus both historical spellings, so this is the last time.
alter table groups drop constraint if exists groups_admin_day_time_key;
alter table groups drop constraint if exists groups_admin_id_day_of_week_start_time_key;
alter table groups drop constraint if exists groups_day_of_week_start_time_key;

-- A unique CONSTRAINT is backed by an index that goes with it, but an index
-- created separately would not - drop that spelling too.
drop index if exists groups_admin_day_time_key;
