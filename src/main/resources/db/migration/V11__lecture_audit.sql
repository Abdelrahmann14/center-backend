-- Audit fields for lectures: who created / last edited, and when.

alter table lectures
  add column if not exists created_by text,
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists updated_by text;
