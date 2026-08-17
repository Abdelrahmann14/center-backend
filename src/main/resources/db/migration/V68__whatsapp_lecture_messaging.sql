-- Per-lecture WhatsApp attendance/absence sends + per-template image rendering.
--
-- The two automated messages are no longer scheduled. Attendance now fires only
-- when a (group, lecture) is opted in on the registration page (wa_attendance_optin),
-- and absence is sent by a button inside the group on the Lessons page. To keep a
-- student from getting the same lesson's message twice, wa_message_log records the
-- lecture/group each message belonged to, and each automation carries a flag that
-- switches its delivery from plain text to a rendered image.

alter table wa_message_log add column lecture_id uuid;
alter table wa_message_log add column group_id   uuid;

-- Backs the "already sent for this lesson" dedup query.
create index wa_message_log_dedup_idx
    on wa_message_log (admin_id, lecture_id, origin, status);

alter table wa_message_automation
    add column send_as_image boolean not null default false;

-- One row per (workspace, lecture, group) whose attendance message auto-sends the
-- moment a student in it is marked present. Absence has no opt-in - it is manual.
create table wa_attendance_optin (
    id         uuid primary key,
    admin_id   uuid    not null,
    lecture_id uuid    not null,
    group_id   uuid    not null,
    enabled    boolean not null default false,
    created_at timestamptz not null default now(),
    created_by varchar(255),
    updated_at timestamptz not null default now(),
    updated_by varchar(255),
    version    bigint  not null default 0,
    constraint wa_attendance_optin_uq unique (admin_id, lecture_id, group_id)
);
