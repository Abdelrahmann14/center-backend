-- WhatsApp Cloud API (official, hosted by Meta) alongside the existing Green API.
--
-- The two are different products, not two settings of one: Green API drives a
-- phone that is logged into the WhatsApp app, while Cloud API takes the number
-- over entirely and only ever sends approved templates outside a 24-hour reply
-- window. A number can live on ONE of them, never both.
--
-- Rather than a second table, an instance row grows a `provider` and the fields
-- the other provider needs. Everything that resolves a number to send with -
-- the responsibility assignments, the per-admin pool, the failover - keeps
-- working untouched, because it all keys off `state = 'authorized'`, which a
-- registered Cloud number sets just like a linked Green one.

alter table whatsapp_instance
    add column provider        varchar(20)  not null default 'GREEN_API',
    add column phone_number_id varchar(40),
    add column waba_id         varchar(40),
    add column display_name    varchar(120),
    add column quality_rating  varchar(20);

-- Green's credentials are its own; a Cloud row has none of them (the token is
-- the platform's, held in the environment, not per number).
alter table whatsapp_instance alter column instance_id drop not null;
alter table whatsapp_instance alter column api_token  drop not null;

-- Meta's own id for the number. One row per number, and only for Cloud rows.
create unique index whatsapp_instance_phone_number_id_key
    on whatsapp_instance (phone_number_id)
 where phone_number_id is not null;

-- Delivery is asynchronous on Cloud API: the send call only returns an id, and
-- whether it arrived comes back later on the webhook. The log row is matched by
-- that id.
alter table wa_message_log
    add column provider     varchar(20) not null default 'GREEN_API',
    add column wamid        varchar(80),
    add column delivered_at timestamptz,
    add column read_at      timestamptz;

create index wa_message_log_wamid_idx on wa_message_log (wamid) where wamid is not null;

-- A mirror of the templates that live in Meta's WhatsApp Manager, so the app can
-- show what it is allowed to send without calling out on every page load. Meta
-- stays the source of truth: rows are refreshed from it, never invented here.
create table wa_cloud_template (
    id                uuid primary key default gen_random_uuid(),
    meta_template_id  varchar(40)  not null unique,
    name              varchar(512) not null,
    language          varchar(20)  not null,
    category          varchar(30)  not null,
    -- APPROVED | PENDING | REJECTED | PAUSED | DISABLED
    status            varchar(30)  not null,
    -- The BODY component's text, with its {{1}} placeholders, for the picker UI.
    body_text         text,
    -- NONE | TEXT | IMAGE | DOCUMENT | VIDEO
    header_format     varchar(20)  not null default 'NONE',
    -- How many {{n}} the body takes, so a caller can be checked before sending.
    body_params       int          not null default 0,
    rejected_reason   text,
    synced_at         timestamptz  not null default now(),
    created_at        timestamptz  not null default now()
);

create index wa_cloud_template_status_idx on wa_cloud_template (status);

-- Which Meta template each automation sends when its number is a Cloud number.
-- Null = not mapped yet, which is why an automation can be enabled long before
-- Meta has approved anything: the Green path keeps working meanwhile.
alter table wa_message_automation add column cloud_template_name varchar(512);
