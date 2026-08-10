-- Centers (السناتر) - tutoring locations, active/inactive.

create table if not exists centers (
  id         uuid primary key default gen_random_uuid(),
  name       text not null unique,
  is_active  boolean not null default true,
  created_at timestamptz not null default now()
);
