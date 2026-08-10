-- Super-admin controlled per-admin enable flag for the WhatsApp numbers feature
-- (mirrors google_contacts_config). Not tenant-filtered: the super admin (no
-- tenant) writes it for a chosen admin.
CREATE TABLE whatsapp_config (
    admin_id   uuid PRIMARY KEY,
    enabled    boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
