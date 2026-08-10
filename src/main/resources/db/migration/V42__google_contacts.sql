-- Google Contacts (People API) synchronization. All tables carry an explicit
-- admin_id (NOT tenant-filtered) so both admin requests and the background sync
-- thread can access them by admin without a tenant context.

-- Super-admin controlled per-admin enable flag. No @TenantId: the super admin
-- (who has no tenant) writes it for a chosen admin.
CREATE TABLE google_contacts_config (
    admin_id   uuid PRIMARY KEY,
    enabled    boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Connected Google accounts (an admin may connect several; contacts sync to all).
-- refresh_token / access_token are secrets held only in the DB.
CREATE TABLE google_account (
    id            uuid PRIMARY KEY,
    admin_id      uuid NOT NULL,
    email         text NOT NULL,
    refresh_token text NOT NULL,
    access_token  text,
    access_expiry timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX google_account_admin_email_key ON google_account (admin_id, email);
CREATE INDEX google_account_admin_idx ON google_account (admin_id);

-- Per-admin, per-grade contact naming marks. All three optional; blank = omit.
--   student_mark - suffix when the number is a student's
--   parent_mark  - suffix when the number is a parent's
--   both_mark    - suffix when the same number is both student's and parent's
CREATE TABLE grade_contact_mark (
    id           uuid PRIMARY KEY,
    admin_id     uuid NOT NULL,
    grade_id     uuid NOT NULL REFERENCES grades (id) ON DELETE CASCADE,
    student_mark text,
    parent_mark  text,
    both_mark    text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX grade_contact_mark_key ON grade_contact_mark (admin_id, grade_id);

-- Maps a synced subject (student/parent/both) + phone to its Google contact per
-- connected account, so updates target the right contact and no duplicates form.
CREATE TABLE google_contact_link (
    id                uuid PRIMARY KEY,
    admin_id          uuid NOT NULL,
    google_account_id uuid NOT NULL REFERENCES google_account (id) ON DELETE CASCADE,
    subject_type      text NOT NULL,
    subject_id        uuid NOT NULL,
    phone             text NOT NULL,
    resource_name     text NOT NULL,
    etag              text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX google_contact_link_subject_idx ON google_contact_link (admin_id, subject_type, subject_id);
CREATE UNIQUE INDEX google_contact_link_acct_phone_key ON google_contact_link (google_account_id, phone);
