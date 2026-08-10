-- Grades become a single global master list, managed only by the super admin
-- (previously each Admin owned its own per-tenant grades). References elsewhere
-- (center_grades, students.grade) are by name text, not by grade id, so dedup by
-- name orphans nothing.

-- 1) Collapse duplicate names across workspaces, keeping one row per name.
DELETE FROM grades g USING grades g2
  WHERE g.name = g2.name AND g.ctid > g2.ctid;

-- 2) Drop tenant scoping. CASCADE also removes the (admin_id, name) unique and
-- the admin_id foreign key that depend on the column.
ALTER TABLE grades DROP COLUMN IF EXISTS admin_id CASCADE;

-- 3) Names are now globally unique.
CREATE UNIQUE INDEX IF NOT EXISTS grades_name_key ON grades (name);
