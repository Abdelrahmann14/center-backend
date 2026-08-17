-- Automated + logged WhatsApp messaging.
--
-- Two automated message types (ATTENDANCE, ABSENCE) each carry an admin-written
-- base message plus AI-generated alternatives; at send time one variant is picked
-- at random so recipients do not all get identical wording. Every WhatsApp message
-- the system sends - automated or a manual broadcast - is recorded per recipient
-- in wa_message_log, which backs the Messages page history table.

create table wa_message_automation (
    id             uuid primary key,
    admin_id       uuid not null,
    type           varchar(20)  not null,                    -- ATTENDANCE | ABSENCE
    enabled        boolean      not null default false,
    audience       varchar(10)  not null default 'STUDENT',  -- STUDENT | PARENT | BOTH
    week_start_day smallint,                                 -- 0=Sat..6=Fri, ABSENCE only
    week_end_day   smallint,                                 -- 0=Sat..6=Fri, ABSENCE only
    created_at     timestamptz  not null default now(),
    created_by     varchar(255),
    updated_at     timestamptz  not null default now(),
    updated_by     varchar(255),
    version        bigint       not null default 0,
    constraint wa_message_automation_admin_type_uq unique (admin_id, type)
);

create table wa_message_variant (
    id            uuid primary key,
    admin_id      uuid not null,
    automation_id uuid not null references wa_message_automation(id) on delete cascade,
    body          text not null,
    sort_order    int  not null default 0,      -- 0 = base (admin-written), 1.. = AI alternatives
    created_at    timestamptz not null default now(),
    created_by    varchar(255),
    updated_at    timestamptz not null default now(),
    updated_by    varchar(255),
    version       bigint      not null default 0
);
create index wa_message_variant_automation_idx on wa_message_variant (automation_id, sort_order);

create table wa_message_log (
    id              uuid primary key,
    admin_id        uuid        not null,
    recipient_name  varchar(255),
    phone           varchar(40),
    recipient_code  varchar(40),                 -- the student's serial, as text
    recipient_type  varchar(10) not null,        -- STUDENT | PARENT
    student_id      uuid,
    body            text        not null,
    status          varchar(10) not null,        -- SENT | FAILED
    failure_reason  text,
    source          varchar(10) not null,        -- SYSTEM | MANUAL
    origin          varchar(20) not null,        -- ATTENDANCE | ABSENCE | MANUAL
    sent_by_user_id uuid,                         -- the user who pressed send (MANUAL only)
    sent_by_name    varchar(255),
    created_at      timestamptz not null default now()
);
create index wa_message_log_admin_created_idx on wa_message_log (admin_id, created_at desc);
