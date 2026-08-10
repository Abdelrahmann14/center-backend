-- The super admin now runs a POOL of WhatsApp numbers (each its own Green API
-- instance) instead of a single one. Drop the single-instance constraints, add a
-- friendly label, and keep each Green API instance unique.
DROP INDEX IF EXISTS whatsapp_instance_super_key;
DROP INDEX IF EXISTS whatsapp_instance_owner_key;

ALTER TABLE whatsapp_instance ADD COLUMN IF NOT EXISTS label text;

CREATE UNIQUE INDEX whatsapp_instance_instid_key ON whatsapp_instance (instance_id);

-- Which connected number is responsible for each WhatsApp send purpose. A purpose
-- (code) is owned by at most one number (code is the primary key). Deleting a
-- number frees its purposes; the service reassigns them to a backup number
-- (automatic failover) before/independently of this cascade.
CREATE TABLE whatsapp_responsibility (
    code        text PRIMARY KEY,
    instance_id uuid NOT NULL REFERENCES whatsapp_instance (id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX whatsapp_responsibility_inst_idx ON whatsapp_responsibility (instance_id);
