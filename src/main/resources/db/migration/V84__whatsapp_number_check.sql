-- Whether a phone number is on WhatsApp at all.
--
-- Deliberately NOT tenant-scoped, and the only table in the schema that is not.
-- Every other fact here belongs to one teacher's workspace; this one belongs to
-- the number. "01012345678 has WhatsApp" is the same answer whoever asks, so
-- scoping it per admin would mean paying to ask the same question again for
-- every teacher who happens to have that guardian on their roster - a shared
-- parent between two teachers is one row, checked once.
--
-- The answer comes from Green API, which is used for this and nothing else. No
-- message is ever sent through it.
create table if not exists wa_number_check (
    -- The local Egyptian form the roster stores (01xxxxxxxxx), so a lookup from
    -- a student row is a primary-key hit with no normalisation at read time.
    phone            text        primary key,
    exists_whatsapp  boolean     not null,
    -- Re-asked once this goes past the configured TTL. A number can gain or lose
    -- WhatsApp, and without an age a first wrong answer would be permanent.
    checked_at       timestamptz not null default now()
);

comment on table wa_number_check is
    'Platform-wide cache of "is this number on WhatsApp". Not tenant-scoped: the answer belongs to the number.';
