-- Birth date, collected during student self-registration. Nullable so the
-- existing records (created by teachers before this field existed) stay valid.
alter table students add column if not exists birth_date date;
