-- Extended student profile + dynamic academic tracks per grade.

alter table students
  add column if not exists grade          text,
  add column if not exists student_phones text[] not null default '{}',
  add column if not exists parent_phones  text[] not null default '{}',
  add column if not exists religion       text,
  add column if not exists academic_track text,
  add column if not exists notes          text;

-- Grade track kind drives the student form's الشعبة options:
-- 'none' | 'g11' (علمي/أدبي) | 'g12' (علمي علوم/علمي رياضة/أدبي).
alter table grades
  add column if not exists track_kind text not null default 'none'
    check (track_kind in ('none', 'g11', 'g12'));
