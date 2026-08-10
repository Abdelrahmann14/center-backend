-- Per-publish content versioning + per-group exam passwords.
--
-- content_version bumps on every content edit (questions/settings/schedule).
-- published_version records the content_version at the last publish, so the
-- student-facing "live" version only advances when the admin re-publishes. A
-- downloaded copy is outdated when its version is below the live one.
ALTER TABLE exams ADD COLUMN IF NOT EXISTS content_version integer NOT NULL DEFAULT 1;
ALTER TABLE exams ADD COLUMN IF NOT EXISTS published_version integer;

-- One password per (exam, group), regenerated on every publish. The admin views
-- every group's password on the exam page; a student only ever receives their
-- own group's password when they download the exam.
CREATE TABLE IF NOT EXISTS exam_group_passwords (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id    uuid NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    group_id   uuid NOT NULL,
    password   text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (exam_id, group_id)
);
CREATE INDEX IF NOT EXISTS exam_group_passwords_exam_idx ON exam_group_passwords (exam_id);
