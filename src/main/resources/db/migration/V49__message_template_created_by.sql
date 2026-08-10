-- Message templates carried updated_by but nothing wrote it, and had no
-- created_by at all. Both tables' audit columns are now filled by JPA auditing
-- (AuditingEntityListener), so the super admin's table can show who created a
-- template and who last edited it. Existing rows stay NULL, and the UI renders
-- a dash for them.
ALTER TABLE message_templates ADD COLUMN IF NOT EXISTS created_by varchar(255);
