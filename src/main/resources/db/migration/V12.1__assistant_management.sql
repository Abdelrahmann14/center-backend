-- Assistant management: pay rate + notes on users, and login/logout work
-- sessions used to compute monthly attendance (and derived salary).

alter table users
  add column if not exists daily_rate numeric(10, 2) not null default 0,
  add column if not exists notes      text;

-- One row per assistant login; logout_at filled when they sign out.
-- Attendance for a month = count(distinct login day) in that month.
create table if not exists work_sessions (
  id        uuid primary key default gen_random_uuid(),
  user_id   uuid not null references users (id) on delete cascade,
  login_at  timestamptz not null default now(),
  logout_at timestamptz
);

create index if not exists work_sessions_user_login_idx
  on work_sessions (user_id, login_at);
