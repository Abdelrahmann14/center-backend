-- Expo push tokens, one row per device. A user may have several devices; a token
-- is globally unique and re-registers (upserts) to whichever account last logged
-- in on that device. Not tenant-scoped: pinned to a user account, like
-- notifications.
create table if not exists push_tokens (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references users (id) on delete cascade,
    token      text not null unique,
    platform   text,
    updated_at timestamptz not null default now()
);

create index if not exists push_tokens_user_idx on push_tokens (user_id);
