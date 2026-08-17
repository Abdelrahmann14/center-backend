-- Make the student search stop reading the whole table.
--
-- The search box behind the students page and the registration desk compiles to
--   lower(name) LIKE '%term%' OR lower(school) LIKE '%term%' OR lower(city) LIKE '%term%'
-- and a leading wildcard cannot use a btree index, so every keystroke-debounced
-- search was a sequential scan of students. It is also the single most-used
-- query in the product - the desk searches for a student for every registration -
-- and the web page reads EVERY page of the result, so one search costs several
-- of those scans, not one.
--
-- pg_trgm is the index type built for exactly this predicate: it breaks each
-- value into three-character grams and indexes those, which is what lets an
-- infix LIKE be answered from an index. Patterns shorter than three characters
-- still fall back to a scan; that is inherent to trigrams, and a one-character
-- search is a scan of an already-narrow filter.
--
-- Write cost is the reason these are only on the three columns actually
-- searched. GIN maintenance is real, but a student row is written once and
-- edited rarely, while these columns are searched continuously - so the trade
-- runs heavily in favour of the read.
--
-- The expressions match the generated SQL exactly (lower(col)); an index on the
-- bare column would not be used.

create extension if not exists pg_trgm;

create index if not exists students_name_trgm_idx
  on students using gin (lower(name) gin_trgm_ops);

create index if not exists students_school_trgm_idx
  on students using gin (lower(school) gin_trgm_ops);

create index if not exists students_city_trgm_idx
  on students using gin (lower(city) gin_trgm_ops);
