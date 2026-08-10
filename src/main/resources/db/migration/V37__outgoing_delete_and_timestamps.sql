-- (3) Link each per-recipient notification back to the broadcast that produced
-- it, so deleting a sent broadcast can also remove it from every recipient inbox.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS outgoing_id uuid;
CREATE INDEX IF NOT EXISTS notifications_outgoing_idx ON notifications (outgoing_id);

-- (9) message_templates only tracked updated_at; add created_at for the "created"
-- column, backfilled from updated_at for existing rows.
ALTER TABLE message_templates ADD COLUMN IF NOT EXISTS created_at timestamptz;
UPDATE message_templates SET created_at = updated_at WHERE created_at IS NULL;
ALTER TABLE message_templates ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE message_templates ALTER COLUMN created_at SET NOT NULL;
