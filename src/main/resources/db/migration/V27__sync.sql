-- Offline sync foundation (phase 1: students + attendance).
--
-- Three pieces: soft deletes so a removal can propagate, an append-only change
-- feed giving every client one monotonic cursor for incremental pull, and an
-- idempotency ledger so a push delivered twice applies once. All scoped per
-- tenant (admin_id); the sync service filters explicitly (it uses JDBC, not the
-- Hibernate @TenantId path).

alter table students add column if not exists deleted_at timestamptz;

-- One attendance mark per student per group per day. Idempotent push relies on
-- this for ON CONFLICT DO NOTHING; dedup any pre-existing collisions first so
-- the unique index can be built.
delete from attendance a
  using attendance b
 where a.ctid < b.ctid
   and a.admin_id = b.admin_id
   and a.group_id = b.group_id
   and a.student_id = b.student_id
   and a.attended_on = b.attended_on;

create unique index if not exists attendance_daily_uidx
  on attendance (admin_id, group_id, student_id, attended_on);

-- The change feed. A global bigserial is the cursor; reads filter to the tenant.
create table if not exists sync_change_log (
  seq        bigserial primary key,
  admin_id   uuid not null,
  entity     text not null,
  row_id     uuid not null,
  op         text not null default 'upsert' check (op in ('upsert', 'delete')),
  changed_at timestamptz not null default now()
);
create index if not exists sync_change_log_admin_seq_idx
  on sync_change_log (admin_id, seq);

create or replace function sync_log_change() returns trigger as $$
begin
  insert into sync_change_log (admin_id, entity, row_id, op)
  values (new.admin_id, tg_argv[0], new.id, 'upsert');
  return new;
end;
$$ language plpgsql;

drop trigger if exists students_sync_log on students;
create trigger students_sync_log
  after insert or update on students
  for each row execute function sync_log_change('student');

drop trigger if exists attendance_sync_log on attendance;
create trigger attendance_sync_log
  after insert on attendance
  for each row execute function sync_log_change('attendance');

-- Idempotency ledger: (tenant, mutation id) applied at most once.
create table if not exists sync_applied_mutations (
  admin_id    uuid not null,
  mutation_id uuid not null,
  applied_at  timestamptz not null default now(),
  primary key (admin_id, mutation_id)
);
