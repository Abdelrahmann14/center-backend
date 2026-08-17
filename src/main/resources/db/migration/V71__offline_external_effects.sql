-- Offline-first round 2: the exact attendance instant, and a durable queue for
-- the work that can only happen while the internet is up.
--
-- 1. registrations.attended_at
--    "When was this student marked present?" used to be answered by created_at,
--    which is the moment the ROW reached the database. For an attendance taken
--    offline that is the moment the device reconnected - minutes or hours after
--    the student actually walked in - so the message variable and the desk
--    screens were quoting the sync time as the attendance time. attended_at is
--    the real instant (to the second), supplied by the device for a queued
--    registration and defaulted to now() for an online one.
--
-- 2. external_effect_outbox
--    Google Contacts and WhatsApp live outside this system, so a save that
--    happens while they are unreachable cannot finish its side effect there and
--    then. Until now those effects were fire-and-forget: the event fired, the
--    call failed, and nothing remembered it should be retried. Every such effect
--    is now written here first and drained by a scheduler, so an effect queued
--    while the line was down runs by itself once it is back.

alter table registrations
    add column attended_at timestamptz;

-- Existing rows: created_at is the best answer we have, and for every row
-- written online it is the right one.
update registrations set attended_at = created_at where attended_at is null;

alter table registrations
    alter column attended_at set default now(),
    alter column attended_at set not null;

create table external_effect_outbox (
    id              uuid        primary key,
    admin_id        uuid        not null,
    -- GOOGLE_CONTACT | WHATSAPP_LECTURE | WHATSAPP_STUDENT_DOC
    kind            varchar(40) not null,
    -- The subject the effect is about (a student, a lesson): lets a repeated
    -- enqueue collapse onto the pending row instead of piling up duplicates.
    ref_id          uuid,
    payload         text,
    attempts        int         not null default 0,
    next_attempt_at timestamptz not null default now(),
    last_error      text,
    created_at      timestamptz not null default now()
);

-- One pending effect per (workspace, kind, subject). A student edited five times
-- while Google was down owes Google one sync, not five.
create unique index external_effect_outbox_subject_uq
    on external_effect_outbox (admin_id, kind, ref_id)
    where ref_id is not null;

create index external_effect_outbox_due_idx
    on external_effect_outbox (next_attempt_at);

-- Back-fill: every student that has no Google contact link yet is, by
-- definition, an effect that never completed. Queue them so the drainer picks
-- them up on its first pass instead of waiting for the next edit.
insert into external_effect_outbox (id, admin_id, kind, ref_id)
select gen_random_uuid(), s.admin_id, 'GOOGLE_CONTACT', s.id
  from students s
 where exists (select 1 from google_contacts_config c
                where c.admin_id = s.admin_id and c.enabled)
   and not exists (select 1 from google_contact_link l
                    where l.admin_id = s.admin_id and l.subject_id = s.id)
on conflict do nothing;
