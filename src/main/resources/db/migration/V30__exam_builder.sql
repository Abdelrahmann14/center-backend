-- Exam builder enhancements. Exam-level settings live on `exams`; per-question
-- score / multi-correct / bonus / note live on `exam_questions`. All additive and
-- defaulted, so existing rows keep working (score defaults to 1, everything off).
alter table exams
  add column if not exists label_style            text    not null default 'arabic',
  add column if not exists allow_multiple_correct boolean not null default false,
  add column if not exists notes_enabled          boolean not null default false,
  add column if not exists bonus_enabled          boolean not null default false;

alter table exam_questions
  -- Each regular question carries a score; the sum must equal the exam max score.
  add column if not exists score          numeric(5, 2) not null default 1,
  -- When the exam allows it, a question may accept more than one correct choice.
  add column if not exists allow_multiple boolean       not null default false,
  -- Bonus questions score independently and are excluded from the max-score sum.
  add column if not exists is_bonus       boolean       not null default false,
  add column if not exists bonus_score    numeric(5, 2),
  -- Optional note shown above the question (speech-bubble) for students.
  add column if not exists note           text;
