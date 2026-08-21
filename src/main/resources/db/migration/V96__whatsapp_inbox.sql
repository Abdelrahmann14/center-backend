-- ---------------------------------------------------------------------------
-- The other direction.
--
-- Everything WhatsApp-shaped in this system so far has been one-way: the app
-- speaks, a parent listens, and whatever the parent typed back arrived at the
-- webhook and was written to a log line and thrown away:
--
--     log.info("Inbound WhatsApp message from {}", message.path("from"))
--
-- That is a real loss. A parent who replies "الرقم ده مش بتاع ابني" or "هو ليه
-- غايب النهاردة؟" is talking to a wall, and the teacher never learns they wrote.
--
-- It is also the one and only way this system may send free text. Meta lets a
-- business answer a person freely for 24 hours after THAT PERSON's last message
-- - the "customer service window". Inside it: any wording, no template, no
-- review, and no charge (service conversations became free on 2025-11-03, and
-- they never counted against the messaging limit). Outside it: template only.
-- So the inbox is not a nicety bolted onto the broadcast feature - it is the
-- only place in the product where a human can write a sentence of their own to
-- a parent and have it arrive.
--
-- Three tables: who is talking to us, what was said, and the files.
-- ---------------------------------------------------------------------------


-- ── 1. One person, one thread ──────────────────────────────────────────────
--
-- Keyed by (admin_id, phone) rather than by student: a phone is what WhatsApp
-- addresses and what the webhook carries, and one number is often the parent of
-- three siblings. Matching that number to a student is a CONVENIENCE resolved
-- at ingest and cached on the row - the thread exists either way, because a
-- message from a stranger is still a message somebody has to answer.
create table wa_conversation (
    id                uuid primary key default gen_random_uuid(),
    admin_id          uuid        not null,

    -- Local form (01xxxxxxxxx), the same spelling wa_message_log and the roster
    -- use, so a thread and the send history join on the obvious column.
    phone             varchar(40) not null,
    -- What Meta calls them (2010...), kept verbatim: the reply is addressed to
    -- the wa_id Meta reported, which is not always the number we dialled.
    wa_id             varchar(40),
    -- The name from the sender's own WhatsApp profile. Not authoritative - a
    -- person types it themselves - so it is a fallback for the header, never a
    -- substitute for the matched student.
    profile_name      varchar(255),

    -- Which of OUR numbers they wrote to. A reply MUST leave from the same
    -- number: the 24-hour window belongs to that pair, and answering from a
    -- different line is a business-initiated message to someone who never
    -- wrote to it - rejected, or worse, delivered as a cold approach.
    phone_number_id   varchar(40),

    -- The roster match, denormalised so the list renders without a join per row.
    student_id        uuid,
    student_name      varchar(255),
    student_code      varchar(40),
    -- STUDENT | PARENT | UNKNOWN - whose number this is, as far as we can tell.
    contact_kind      varchar(10) not null default 'UNKNOWN',

    -- ── window and ordering ──
    -- The whole feature turns on this column. last_inbound_at + 24h is the
    -- moment free text stops being allowed, and the composer is enabled or
    -- disabled by comparing it to now(). Nothing else may decide that: a UI
    -- that guessed would let someone type a paragraph Meta will refuse.
    last_inbound_at   timestamptz,
    last_message_at   timestamptz not null default now(),
    -- IN | OUT, for the tick and the "أنت:" prefix in the list.
    last_direction    varchar(3)  not null default 'IN',
    last_preview      text,

    -- Inbound messages nobody has opened yet. Zeroed when the thread is read.
    unread            integer     not null default 0,
    archived          boolean     not null default false,

    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),

    constraint wa_conversation_phone_uq unique (admin_id, phone)
);

-- The list: newest activity first, unarchived by default.
create index wa_conversation_recent_idx
    on wa_conversation (admin_id, last_message_at desc);

-- The unread badge, which every page in the app polls.
create index wa_conversation_unread_idx
    on wa_conversation (admin_id)
    where unread > 0 and archived = false;

-- "Who from this workspace is this number?" - used when a student is opened
-- from a thread, and when a thread is opened from a student.
create index wa_conversation_student_idx
    on wa_conversation (admin_id, student_id)
    where student_id is not null;

comment on table wa_conversation is
    'One WhatsApp thread per phone per workspace. last_inbound_at + 24h is the free-text window.';
comment on column wa_conversation.phone_number_id is
    'The number they wrote to. A reply must leave from it - the 24h window belongs to the pair.';


-- ── 2. What was said ───────────────────────────────────────────────────────
create table wa_conversation_message (
    id              uuid primary key default gen_random_uuid(),
    admin_id        uuid        not null,
    conversation_id uuid        not null
                    references wa_conversation (id) on delete cascade,

    -- IN  = they wrote to us
    -- OUT = we wrote to them
    direction       varchar(3)  not null,

    -- Meta's own id. UNIQUE, and that is load-bearing: Meta redelivers a webhook
    -- until it gets a 200, and a retried delivery must not paint the same
    -- message into the thread twice. The insert is "on conflict do nothing",
    -- so idempotency is a database fact rather than a hope about retries.
    wamid           varchar(128) unique,

    -- text | image | audio | video | document | sticker | location | contacts
    -- | button | interactive | reaction | order | system | unsupported
    --
    -- Kept as Meta's own word rather than mapped to an internal enum: WhatsApp
    -- adds message types faster than a migration can follow, and an unknown
    -- word renders as "نوع رسالة غير مدعوم" while a rejected insert loses it.
    kind            varchar(20) not null default 'text',
    -- The text of a text message, or a caption, or the readable fallback for a
    -- type this app cannot draw. Always something a human can read.
    body            text,

    -- ── media, when there is any ──
    -- Meta keeps an inbound file for 30 days and hands out a download URL that
    -- expires in minutes, so the id is stored and the bytes are fetched on
    -- first view into wa_media. Downloading everything at webhook time would
    -- put a multi-megabyte transfer inside a handler Meta times out in seconds.
    media_id        varchar(200),
    media_mime      varchar(120),
    media_filename  varchar(255),
    media_size      integer,

    -- ── outbound state ──
    -- QUEUED | SENT | DELIVERED | READ | FAILED for OUT.
    -- RECEIVED for IN.
    status          varchar(12) not null default 'RECEIVED',
    failure_code    integer,
    failure_reason  text,
    delivered_at    timestamptz,
    read_at         timestamptz,

    -- Who pressed send. Null on inbound, and null on anything the system sent
    -- by itself.
    sent_by_user_id uuid,
    sent_by_name    varchar(255),

    -- Meta's clock, not ours: a message that sat in a retry queue for ten
    -- minutes was still SAID when it was said, and a thread ordered by our
    -- receipt time would put it after replies that answer it.
    occurred_at     timestamptz not null default now(),
    created_at      timestamptz not null default now()
);

-- The thread, newest last. Every read this feature does is this query.
create index wa_conversation_message_thread_idx
    on wa_conversation_message (conversation_id, occurred_at, created_at);

-- Delivery receipts arrive by wamid with no tenant attached (see the webhook
-- service) - already covered by the unique constraint above.

comment on table wa_conversation_message is
    'Every WhatsApp message either way, inside a conversation. wamid unique = webhook retries are safe.';
comment on column wa_conversation_message.occurred_at is
    'Meta''s timestamp. Ordering by our own receipt time would reorder a delayed message after its reply.';


-- ── 3. The files ───────────────────────────────────────────────────────────
--
-- Separate from the message row so a thread of two hundred messages is two
-- hundred small rows, and a 16 MB video is fetched only by the request that
-- displays it. Postgres would TOAST the column out of line anyway; the split
-- makes the intent explicit and lets the cache be cleared without touching the
-- conversation.
create table wa_media (
    message_id  uuid primary key
                references wa_conversation_message (id) on delete cascade,
    admin_id    uuid        not null,
    mime        varchar(120),
    filename    varchar(255),
    content     bytea       not null,
    size_bytes  integer     not null,
    created_at  timestamptz not null default now()
);

comment on table wa_media is
    'Inbound files, fetched from Meta on first view. Meta deletes the original after 30 days.';


-- ── 4. The reply path needs a tenant ───────────────────────────────────────
--
-- A webhook arrives with no session and no tenant, so the workspace has to be
-- worked out from the payload. The first rule is the number's owner. The second
-- - for a message sent to the platform's shared number - is "whoever last wrote
-- to this person", which is this index.
create index if not exists wa_message_log_phone_recent_idx
    on wa_message_log (phone, created_at desc)
    where phone is not null;
