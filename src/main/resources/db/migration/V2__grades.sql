-- Grades (الصفوف) - feed the group + student forms.

create table if not exists grades (
  id         uuid primary key default gen_random_uuid(),
  name       text not null unique,
  created_at timestamptz not null default now()
);
