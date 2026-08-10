-- Who actually sent a notification, so the inbox can show the teacher's photo
-- next to their name. Resolved at read time (not copied), so a teacher changing
-- their picture updates every notification they ever sent.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS sender_user_id uuid;
CREATE INDEX IF NOT EXISTS notifications_sender_idx ON notifications (sender_user_id);
