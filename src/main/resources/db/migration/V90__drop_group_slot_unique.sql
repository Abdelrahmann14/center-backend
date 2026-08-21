-- Two groups may now share a day and a minute.
--
-- The rule was written when a group could only be deleted for real. V69 made
-- deletion SOFT - the row stays so old registrations and attendance still resolve
-- its label - and the constraint went on counting those rows. So a slot freed
-- months ago stayed permanently occupied by something no screen draws: moving a
-- group to Saturday 19:00 was refused because a deleted group had once been
-- there, and the teacher was told a group exists at a time where the calendar
-- plainly shows an empty cell.
--
-- The key also never included center_name, so it was never really "one group per
-- slot" either - it was one group per slot across every center at once.
--
-- Rather than patch it into a partial index over `not deleted`, it goes. A slot
-- clash is a fact about a teacher's day that they can see on the timetable and
-- judge for themselves; it is not an integrity rule the database should be
-- enforcing against them.
alter table groups drop constraint if exists groups_admin_id_day_of_week_start_time_key;
-- The pre-V16 global name, in case an older database still carries it.
alter table groups drop constraint if exists groups_day_of_week_start_time_key;
