-- The lesson notes field and the "attending assistants" list were removed from
-- the product, so the columns go with them.
alter table lectures drop column if exists notes;
alter table lectures drop column if exists attending_assistants;
