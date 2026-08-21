-- Remove the mobile app.
--
-- The student and guardian apps are gone. Everything below existed only to serve
-- them: accounts they signed in with, the codes that created those accounts, the
-- exams they sat on a phone, the device tokens they were pushed to, and the
-- in-app notification broadcaster that wrote to their inboxes.
--
-- What is NOT touched: the student RECORD, its parent_phones, and every message
-- the teacher sends. A guardian was always reached by phone number on the
-- student's card, never through an account, so WhatsApp keeps working exactly as
-- it did.
--
-- Written to be safe on a database where some of this was never created, and
-- ordered child-before-parent so no foreign key is ever violated.

-- ---------------------------------------------------------------------------
-- 1. The app's own tables.
-- ---------------------------------------------------------------------------
drop table if exists exam_answers cascade;
drop table if exists exam_attempts cascade;
drop table if exists push_tokens cascade;
drop table if exists student_verification_codes cascade;
drop table if exists parent_verification_codes cascade;
drop table if exists parent_student_links cascade;
drop table if exists parents cascade;

-- The in-app broadcaster: a template library and a send history that only ever
-- addressed student and guardian inboxes. Teacher messaging is WhatsApp, whose
-- wording lives in wa_message_automation and whose history is wa_message_log.
drop table if exists outgoing_messages cascade;
drop table if exists message_templates cascade;

-- ---------------------------------------------------------------------------
-- 2. The accounts themselves. Their inbox rows cascade off users.
-- ---------------------------------------------------------------------------
delete from users where role in ('student', 'parent');

-- What is left in the inbox is the platform speaking to a teacher, which needs
-- neither a sender account (for a photo) nor a broadcast it belonged to.
drop index if exists notifications_outgoing_idx;
drop index if exists notifications_sender_idx;
alter table notifications drop column if exists outgoing_id;
alter table notifications drop column if exists sender_user_id;

-- Only three ranks can sign in now, and the constraint should say so rather
-- than leaving the door open for a row nothing in the code can authenticate.
alter table users drop constraint if exists users_role_check;
alter table users
    add constraint users_role_check
    check (role in ('super_admin', 'admin', 'user'));

-- ---------------------------------------------------------------------------
-- 3. The link from a student record to their app account.
-- ---------------------------------------------------------------------------
drop index if exists students_user_id_idx;
alter table students drop column if exists user_id;

-- ---------------------------------------------------------------------------
-- 4. WhatsApp purposes that only the app produced.
--
-- Verification codes, password resets and guardian-link notices. Dropping the
-- assignment rows keeps the responsibilities screen honest: a code the catalog
-- no longer offers would otherwise sit in the table forever, owning a number.
-- ---------------------------------------------------------------------------
delete from whatsapp_responsibility
 where code in ('student_verification', 'student_password_reset',
                'parent_password_reset', 'parent_link_approved_wa',
                'parent_link_rejected_wa');
