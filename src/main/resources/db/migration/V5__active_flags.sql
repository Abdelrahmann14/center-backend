-- Active/inactive toggles for grades + groups (centers already had one from 003).

alter table grades add column if not exists is_active boolean not null default true;
alter table groups add column if not exists is_active boolean not null default true;
