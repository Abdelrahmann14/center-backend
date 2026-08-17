-- Remember the name we last wrote to Google for each contact.
--
-- The "check all numbers" pass compares what the system wants a contact to be
-- called against what Google reports. Google does not always report back the
-- string it was given: it parses a name into parts and rebuilds a display name
-- from them, so a name it has normalised (a dropped bidi mark, collapsed
-- spacing) reads as "different" on every single pass - and every pass renames it
-- again, forever, reporting a handful of corrections that never go away.
--
-- With the written name recorded, a difference Google introduced itself is
-- recognised for what it is: already done, nothing to write.
alter table google_contact_link
  add column if not exists display_name text;
