-- ---------------------------------------------------------------------------
-- Sending within Meta's daily ceiling, without anyone having to think about it.
--
-- Meta caps a business at N unique recipients per rolling 24 hours - 250 until
-- the business is verified, then 2,000, 10,000 and up. Two properties of that
-- cap decide this whole design:
--
--   1. It is counted per BUSINESS PORTFOLIO, not per phone number (Meta moved
--      it on 2025-10-07). Every teacher's number sits in the same portfolio, so
--      they share ONE allowance. One teacher's 500-student lesson can leave
--      every other teacher dark for the rest of the day.
--   2. Meta publishes the LIMIT but not the CONSUMPTION. There is no endpoint
--      that answers "how much is left". It has to be counted here.
--
-- Before this, a send button looped over the roster and fired one blocking HTTP
-- call per recipient with no cap, no pacing and no idea of the ceiling. Past the
-- limit every remaining call was rejected, recorded as a red row, and the loop
-- carried straight on - 750 guaranteed rejections in five minutes, invisible
-- unless someone scrolled the log. A rejection is not free either: a run of them
-- is exactly what degrades a number's quality rating.
--
-- So a button no longer sends. It ENQUEUES, and a drain job spends the allowance
-- as it becomes available. A lesson of 100 with 45 left goes out as 45 now and
-- 55 when the window rolls, by itself.
-- ---------------------------------------------------------------------------


-- ── 1. What Meta currently allows ──────────────────────────────────────────
--
-- One row, ever. The messaging limit belongs to the portfolio, and this system
-- has exactly one - so a per-number table would invite the wrong question ("how
-- much has THIS number got left?") whose true answer is always "the same pool as
-- everyone else". The boolean primary key with a CHECK is the standard way to
-- make a singleton a database fact rather than a convention.
--
-- Never tenant-scoped. A teacher may not raise their own ceiling, and every
-- teacher reads the same number.
create table wa_quota (
    id                boolean primary key default true check (id),

    -- Unique recipients per rolling 24h that Meta will accept. Read from
    -- whatsapp_business_manager_messaging_limit; 250 is Meta's own starting
    -- tier and therefore the only safe default before the first refresh.
    tier              integer      not null default 250,

    -- Meta's own wording for that tier (TIER_250, TIER_2K, TIER_UNLIMITED...),
    -- kept verbatim so a screen can show what Meta says rather than a
    -- translation of it that may drift when Meta renames a tier.
    tier_label        varchar(40),

    -- GREEN | YELLOW | RED | UNKNOWN. Not a limit, but it is what decides
    -- whether the tier ever goes up, so it belongs next to it.
    quality_rating    varchar(20),

    -- CONNECTED | FLAGGED | RESTRICTED | PENDING | BANNED, as Meta reports it.
    number_status     varchar(30),

    -- The mps ceiling Meta reports for the number (STANDARD = 80, HIGH = 1000).
    throughput_level  varchar(20),

    -- A margin held back from every spend decision, because the count here can
    -- never exactly equal Meta's. Meta counts DELIVERED messages; this counts
    -- ACCEPTED ones, and the gap between the two is a webhook away. Spending the
    -- last recipient of the allowance on an estimate is how a run ends in
    -- rejections instead of a clean stop.
    safety_margin     integer      not null default 10,

    -- Null until the first successful read. A stale timestamp is the signal that
    -- the tier on this row is a guess, and the UI says so rather than presenting
    -- a number it cannot vouch for.
    refreshed_at      timestamptz,
    last_error        text,

    updated_at        timestamptz  not null default now()
);

insert into wa_quota (id) values (true) on conflict do nothing;

comment on table wa_quota is
    'Meta''s current messaging ceiling for the whole platform. One row, never tenant-scoped.';
comment on column wa_quota.safety_margin is
    'Recipients held back. Local counting is of accepted sends; Meta counts delivered ones.';


-- ── 2. The queue ───────────────────────────────────────────────────────────
--
-- A row is one message to one recipient: already rendered, already addressed,
-- waiting only for allowance. Rendering happens at enqueue time on purpose - the
-- teacher's name, the lesson time and the grade are true at the moment the
-- button was pressed, and a message that goes out four hours later must say what
-- it would have said then, not what the database looks like now.
create table wa_send_queue (
    id              uuid primary key default gen_random_uuid(),
    admin_id        uuid        not null,

    -- One press of one button. Everything the caller needs to report progress
    -- back to that press hangs off this.
    batch_id        uuid        not null,
    -- Position within the batch, so a lesson leaves in roster order instead of
    -- whatever order the drain job happened to claim rows in.
    seq             integer     not null,

    -- ── recipient (denormalised, exactly as wa_message_log stores it) ──
    -- Copies, not joins: a student deleted between enqueue and send must still
    -- produce an honest log row, and the row must still say who it went to.
    phone           varchar(40) not null,
    recipient_name  varchar(255),
    recipient_code  varchar(40),
    recipient_type  varchar(10) not null,   -- STUDENT | PARENT
    student_id      uuid,

    -- ── payload ──
    body            text        not null,
    -- The variables the body was rendered from. The template send needs them
    -- again to fill its numbered placeholders, and re-deriving them later would
    -- reproduce today's data, not the data the message was written against.
    vars            jsonb,
    source          varchar(10) not null,   -- SYSTEM | MANUAL
    origin          varchar(20) not null,   -- ATTENDANCE | ABSENCE | ...
    lecture_id      uuid,
    group_id        uuid,
    sent_by_user_id uuid,
    sent_by_name    varchar(255),

    -- ── state ──
    -- PENDING   waiting for allowance
    -- SENDING   claimed by a drain pass (crash-visible, reclaimed after a lease)
    -- SENT      Meta accepted it; wa_message_log now owns the outcome
    -- FAILED    permanently, with a code that says why
    -- CANCELLED a human called it off, or the batch was superseded
    state           varchar(10) not null default 'PENDING',

    attempts        smallint    not null default 0,
    -- Backoff, and also the throttle hook: a 130429 pushes every claimed row's
    -- next attempt forward rather than burning its attempt budget.
    next_attempt_at timestamptz not null default now(),
    -- Set while SENDING. A process killed mid-send leaves rows stuck otherwise;
    -- the drain reclaims anything whose lease has expired.
    leased_until    timestamptz,

    failure_code    integer,
    failure_reason  text,
    -- The wa_message_log row this became, once it went.
    log_id          uuid,

    created_at      timestamptz not null default now(),
    finished_at     timestamptz
);

-- The drain's only query: oldest due work first, across all tenants, because
-- the allowance is shared and fairness has to be decided globally.
create index wa_send_queue_due_idx
    on wa_send_queue (next_attempt_at, seq)
    where state = 'PENDING';

-- Reclaiming rows abandoned by a killed process.
create index wa_send_queue_lease_idx
    on wa_send_queue (leased_until)
    where state = 'SENDING';

-- "How is my batch doing" - the progress the button polls.
create index wa_send_queue_batch_idx on wa_send_queue (admin_id, batch_id, state);

-- "What is still waiting for this lesson" - the badge on the lesson row.
create index wa_send_queue_lecture_idx
    on wa_send_queue (admin_id, lecture_id, origin)
    where lecture_id is not null and state in ('PENDING', 'SENDING');

-- One message per student per lesson per kind, once.
--
-- The old guard was "has wa_message_log a SENT row for this student and this
-- lesson", which worked only because sending was synchronous. With a queue there
-- is a window - minutes or hours wide - where a message is owed but not yet
-- logged, and a teacher pressing the button twice in that window would queue the
-- roster twice. CANCELLED and FAILED are excluded so a call-off or a permanent
-- failure can legitimately be re-queued.
create unique index wa_send_queue_once_idx
    on wa_send_queue (lecture_id, origin, student_id, recipient_type)
    where lecture_id is not null and student_id is not null
      and state in ('PENDING', 'SENDING', 'SENT');

comment on table wa_send_queue is
    'Messages rendered and waiting for daily allowance. Drained by WhatsappSendQueueDrainJob.';
comment on column wa_send_queue.vars is
    'Template placeholder values, captured at enqueue time so a late send says what it meant then.';


-- ── 3. The log learns what the queue knows ─────────────────────────────────
--
-- failure_code has existed since V83 but only the webhook ever wrote it, and
-- only for failures Meta reported later. A synchronous rejection - the rate
-- limit, the closed window, the restricted number - carried its code in the
-- response body and the code threw it away, collapsing every one of them into
-- the same Arabic sentence. Without the number, "retry in thirty seconds" and
-- "stop before you get banned" are indistinguishable.
--
-- batch_id ties a logged message back to the press that ordered it.
alter table wa_message_log
    add column if not exists batch_id uuid;

create index if not exists wa_message_log_batch_idx
    on wa_message_log (admin_id, batch_id)
    where batch_id is not null;

-- The counter the whole feature turns on: unique recipients accepted in the last
-- rolling 24 hours, across every tenant. Deliberately NOT filtered by admin_id -
-- Meta counts the portfolio, so anything narrower would report a comfortable
-- number while the real ceiling was already spent.
--
-- 'INBOX' is excluded by the caller, not here: a free-form reply inside a
-- customer's own 24-hour window does not count against the messaging limit.
create index if not exists wa_message_log_quota_idx
    on wa_message_log (created_at, phone)
    where status = 'SENT';
