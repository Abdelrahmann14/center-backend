-- Per-center, per-grade pricing + extra student fields.

create table if not exists center_grades (
  center_id uuid not null references centers(id) on delete cascade,
  grade     text not null,
  price     numeric(10, 2) not null default 0,
  primary key (center_id, grade)
);

alter table students
  add column if not exists gender        text,
  add column if not exists city          text,
  add column if not exists lesson_price  numeric(10, 2),
  add column if not exists is_discounted boolean not null default false;
