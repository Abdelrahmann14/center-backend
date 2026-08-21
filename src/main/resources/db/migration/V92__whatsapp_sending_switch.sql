-- A teacher's own master switch for WhatsApp sending.
--
-- Distinct from `enabled` in the same row, which is the PLATFORM's switch: the
-- super admin decides whether a workspace has the feature at all, and the
-- teacher decides whether it is sending right now. Two different people, two
-- different questions, so two columns rather than one that either could flip
-- out from under the other.
--
-- Defaults to true: turning the feature on for a workspace must not also leave
-- it silently paused.
ALTER TABLE whatsapp_config
    ADD COLUMN IF NOT EXISTS sending_enabled boolean NOT NULL DEFAULT true;
