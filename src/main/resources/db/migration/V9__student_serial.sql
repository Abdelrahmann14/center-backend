-- Sequential student number (starts at 1, never reused).

create sequence if not exists students_serial_seq;

alter table students add column if not exists serial integer;

-- Backfill existing rows in creation order.
with ordered as (
  select id, row_number() over (order by created_at) AS rn from students
)
update students s set serial = o.rn from ordered o
where s.id = o.id and s.serial is null;

-- Advance the sequence past existing max, then use it for future inserts.
select setval('students_serial_seq', coalesce((select max(serial) from students), 0));
alter table students alter column serial set default nextval('students_serial_seq');
alter table students alter column serial set not null;

create unique index if not exists students_serial_uidx on students (serial);
