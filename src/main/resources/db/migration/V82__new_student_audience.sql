-- The new-student welcome carries the barcode card, and the card is the
-- student's own: the code printed on it is what the desk scans to register them,
-- so it has to live on their phone. Both paths that send it now address the
-- student regardless of this column, which leaves any row still saying PARENT
-- describing something that no longer happens.
--
-- Pinned here rather than waiting for the page to save it, because the page no
-- longer offers the choice - there is nothing left to press that would correct
-- the row, so a stale value would sit in the database forever.
update wa_message_automation
set audience = 'STUDENT'
where type = 'NEW_STUDENT'
  and audience is distinct from 'STUDENT';
