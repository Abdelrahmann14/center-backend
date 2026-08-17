-- Widen the registration uniqueness key to include the group, so a student may
-- attend the same lesson AGAIN under a different group (a confirmed repeat /
-- makeup). Same student, same lesson, same group stays unique - a genuine
-- double-registration in one session is still rejected.
--
-- The old key was the inline `unique (lecture_id, student_id)` from
-- V12.2__registrations.sql. An unnamed inline unique gets Postgres's
-- deterministic name <table>_<cols>_key, so drop it by that name (IF EXISTS keeps
-- this safe if it was ever named otherwise). No existing row can violate the
-- wider key - there is at most one row per (lecture, student) today.

ALTER TABLE registrations
  DROP CONSTRAINT IF EXISTS registrations_lecture_id_student_id_key;

ALTER TABLE registrations
  ADD CONSTRAINT registrations_lecture_student_group_key
  UNIQUE (lecture_id, student_id, group_id);
