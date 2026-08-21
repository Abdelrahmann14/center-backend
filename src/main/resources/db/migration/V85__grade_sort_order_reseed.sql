-- Re-seed the grade order, this time reading the names that are actually there.
--
-- V79 looked for the words "إعدادي" and "ثانوي". The grades in this system are
-- named "1ع" and "3ث", so nothing matched: every row fell to the same default
-- and the list came back ordered by name - 1ث, 1ع, 2ث, 2ع - interleaving the two
-- stages. The stage now comes from the letter, which covers both the short names
-- and the spelled-out ones, and إعدادي sorts before ثانوي.

UPDATE grades
SET sort_order =
    -- Stage. إعدادي is tested first because a spelled-out "الثالث الإعدادي"
    -- carries a ث in الثالث; إعدادي never appears inside a ثانوي name.
    CASE
        WHEN name LIKE '%إعداد%' OR name LIKE '%اعداد%' OR name LIKE '%ع%' THEN 0
        WHEN name LIKE '%ثانو%' OR name LIKE '%ث%' THEN 10
        ELSE 20
    END
    +
    -- Year within the stage. Digits (Arabic or Latin) or the ordinal word.
    -- "ثاني" is not a substring of "ثانوي", so the two never collide.
    CASE
        WHEN name LIKE '%1%' OR name LIKE '%١%' OR name LIKE '%أول%' OR name LIKE '%اول%' THEN 1
        WHEN name LIKE '%2%' OR name LIKE '%٢%' OR name LIKE '%ثاني%' OR name LIKE '%ثانية%' THEN 2
        WHEN name LIKE '%3%' OR name LIKE '%٣%' OR name LIKE '%ثالث%' OR name LIKE '%ثالثة%' THEN 3
        ELSE 9
    END;
