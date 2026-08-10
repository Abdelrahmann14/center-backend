-- Publishability flag. An exam is a draft (saved freely) until every validation
-- passes: enough choices, correct answers marked, valid per-question scores, and
-- the regular scores summing to the max. Recomputed on every builder save and on
-- any edit that changes the max score. Only a complete exam may be published.
alter table exams
  add column if not exists complete boolean not null default false;
