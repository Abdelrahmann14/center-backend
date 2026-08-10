-- Super-admin-uploaded profile photos. Stored in-row as bytea (avatars are small
-- and size-capped on upload), served to the super-admin console and the public
-- registration teacher list as a base64 data URL. No external object storage.
alter table users
  add column if not exists photo_data bytea,
  add column if not exists photo_type text;
