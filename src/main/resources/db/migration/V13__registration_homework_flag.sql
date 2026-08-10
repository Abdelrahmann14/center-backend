-- Per-registration homework issue flag (nullable): واجب ناقص / واجب غير معمول /
-- واجب منقول. Empty/null = no issue.

alter table registrations add column if not exists homework_flag text;
