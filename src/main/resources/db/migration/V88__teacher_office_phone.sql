-- A teacher now has two numbers, and they are not the same number.
--
-- users.phone is personal: it shows on the profile and the invoices go to it.
-- Putting it inside a message template would publish it to every parent, so the
-- office line gets its own column - the number a template prints when it says
-- "للتواصل"‏.

ALTER TABLE users ADD COLUMN IF NOT EXISTS office_phone varchar(20);

COMMENT ON COLUMN users.office_phone IS
  'Public contact number printed in message templates; users.phone stays private.';
