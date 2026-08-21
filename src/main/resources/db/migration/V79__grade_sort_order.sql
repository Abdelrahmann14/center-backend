-- Grades get an explicit order.
--
-- Until now the list came back in creation order, which is the order someone
-- happened to type them in - so a grade added later always landed last, and
-- every select in the app (student form, group form, filters) showed the school
-- years out of sequence. Order is a property of the grade, not of when the row
-- was written, so it becomes a column the super admin controls.

ALTER TABLE grades ADD COLUMN IF NOT EXISTS sort_order integer NOT NULL DEFAULT 100;

-- Seed from the names already in the table: إعدادي before ثانوي, and within each
-- stage first/second/third. Anything the patterns do not recognise keeps the
-- default and sorts last by name, where the super admin can renumber it by hand
-- rather than being silently mis-ordered.
UPDATE grades
SET sort_order =
    CASE
        WHEN name LIKE '%إعداد%' OR name LIKE '%اعداد%' THEN 0
        WHEN name LIKE '%ثانو%' THEN 10
        ELSE 100
    END
    +
    CASE
        WHEN name LIKE '%أول%' OR name LIKE '%اول%' OR name LIKE '%1%' THEN 1
        WHEN name LIKE '%ثاني%' OR name LIKE '%ثانية%' OR name LIKE '%2%' THEN 2
        WHEN name LIKE '%ثالث%' OR name LIKE '%ثالثة%' OR name LIKE '%3%' THEN 3
        ELSE 9
    END
WHERE sort_order = 100;

CREATE INDEX IF NOT EXISTS grades_sort_order_idx ON grades (sort_order, name);
