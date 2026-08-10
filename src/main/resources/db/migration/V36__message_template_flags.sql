-- Templates gain an enable flag and a system marker. System templates are the
-- V35-seeded automatic messages: they can be edited and toggled but not deleted.
-- Custom templates (added by the super admin) can be fully managed.
ALTER TABLE message_templates ADD COLUMN enabled   boolean NOT NULL DEFAULT true;
ALTER TABLE message_templates ADD COLUMN is_system boolean NOT NULL DEFAULT false;

-- Every row that exists now is a seeded system template.
UPDATE message_templates SET is_system = true;
