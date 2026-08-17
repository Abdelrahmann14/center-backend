-- Whether a lesson has an exam at all, stated rather than guessed.
--
-- Until now "this lesson had an exam" was inferred, and inferred differently in
-- different places: the analytics counted a missed exam from exam_name, while
-- the score entry screen derived its maximum from exam_grade. A lesson with one
-- filled and the other blank meant different things on different screens, and a
-- lesson with genuinely no exam still showed an empty mark box to be filled.
--
-- Backfilled from what the old rule would have said, so nothing changes meaning
-- for a lesson that already exists.
alter table lectures
  add column if not exists has_exam boolean;

update lectures
   set has_exam = (exam_name  is not null and btrim(exam_name)  <> '')
               or (exam_grade is not null and btrim(exam_grade) <> '')
 where has_exam is null;

alter table lectures alter column has_exam set default true;
alter table lectures alter column has_exam set not null;

-- A lesson with no exam must not carry leftover exam fields, or the two would
-- disagree the moment anything read them directly.
update lectures
   set exam_name = null, exam_grade = null
 where has_exam = false;
