-- Core: users (auth) + groups (teaching sessions).

create extension if not exists pgcrypto;

create table if not exists users (
  id            uuid primary key default gen_random_uuid(),
  username      text not null unique,
  password_hash text not null,
  role          text not null default 'user' check (role in ('admin', 'user')),
  created_at    timestamptz not null default now()
);

create table if not exists groups (
  id           uuid primary key default gen_random_uuid(),
  day_of_week  smallint not null check (day_of_week between 0 and 6),
  start_time   time not null,
  center_name  text not null,
  grade        text not null,
  lesson_price numeric(10, 2),
  created_at   timestamptz not null default now(),
  unique (day_of_week, start_time)
);
