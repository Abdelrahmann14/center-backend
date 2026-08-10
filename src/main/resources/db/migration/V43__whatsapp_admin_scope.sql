-- Extend WhatsApp numbers + responsibilities to admins (teachers), not just the
-- super admin. Numbers already carry owner_admin_id (null = super). Responsibilities
-- become owner-scoped so each admin assigns purposes to their OWN numbers.
--
-- A zero UUID marks the super-admin scope (keeps the column NOT NULL and the
-- (owner, code) uniqueness working for both super and per-admin rows).
ALTER TABLE whatsapp_responsibility
    ADD COLUMN owner_admin_id uuid NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

ALTER TABLE whatsapp_responsibility DROP CONSTRAINT whatsapp_responsibility_pkey;
ALTER TABLE whatsapp_responsibility ADD PRIMARY KEY (owner_admin_id, code);
