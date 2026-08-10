-- Email replaces the username as the login identifier for every account.
-- `username` stays as the human DISPLAY NAME (profile, student info) but no
-- longer authenticates anything. Each role owns its own domain:
--   student -> @center.student.com, assistant -> @center.assistant.com,
--   admin / super admin -> @center.admin.com

alter table users add column if not exists email text;

-- Backfill: sanitise the existing username into a valid local part (letters and
-- digits only) and append the domain for that account's role. Arabic names and
-- spaces sanitise away entirely, hence the 'user' fallback.
update users
   set email = coalesce(nullif(lower(regexp_replace(username, '[^A-Za-z0-9]', '', 'g')), ''), 'user')
             || case role
                  when 'student' then '@center.student.com'
                  when 'user'    then '@center.assistant.com'
                  else                '@center.admin.com'
                end
 where email is null;

-- Different usernames can sanitise to the same local part (e.g. every Arabic
-- name became 'user'). Suffix the collisions so the unique index can be built.
with dupes as (
  select id,
         split_part(email, '@', 1) as local_part,
         split_part(email, '@', 2) as domain,
         row_number() over (partition by lower(email) order by created_at, id) as rn
    from users
)
update users u
   set email = d.local_part || d.rn::text || '@' || d.domain
  from dupes d
 where d.id = u.id
   and d.rn > 1;

alter table users alter column email set not null;

-- Email is globally unique and matched case-insensitively at login.
create unique index if not exists users_email_lower_key on users (lower(email));
