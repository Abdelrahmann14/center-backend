-- Green API WhatsApp instances linked from inside the app. The super admin (and
-- later each teacher) enters an instance id + api token once, then scans the QR
-- shown in the Services page to link a phone - no visit to the Green API console.
--
-- owner_admin_id NULL = the super-admin / platform instance (a single row). A
-- non-null owner scopes the instance to one teacher (future use). base_url lets a
-- self-hosted / regional Green API host override the default.

CREATE TABLE whatsapp_instance (
    id             uuid PRIMARY KEY,
    owner_admin_id uuid REFERENCES users (id) ON DELETE CASCADE,
    instance_id    text NOT NULL,
    api_token      text NOT NULL,
    base_url       text NOT NULL DEFAULT 'https://api.green-api.com',
    phone          text,
    state          text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    created_by     text,
    updated_at     timestamptz NOT NULL DEFAULT now(),
    updated_by     text,
    version        bigint NOT NULL DEFAULT 0
);

-- One instance per teacher, and exactly one super-admin (NULL owner) row.
CREATE UNIQUE INDEX whatsapp_instance_owner_key ON whatsapp_instance (owner_admin_id)
  WHERE owner_admin_id IS NOT NULL;
CREATE UNIQUE INDEX whatsapp_instance_super_key ON whatsapp_instance ((owner_admin_id IS NULL))
  WHERE owner_admin_id IS NULL;
