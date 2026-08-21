-- ---------------------------------------------------------------------------
-- The bell has one job now.
--
-- `notifications` was built for a product that no longer exists: the mobile app
-- had inboxes for students and guardians, and a teacher could broadcast into
-- them. That app was removed in V78, and its rows were left behind - so the bell
-- a teacher opens today can still be holding notices about a broadcast to an
-- audience that has no accounts, from senders whose photos were dropped, linking
-- to screens that were deleted.
--
-- Exactly two things write to this table now:
--
--   'chat'      somebody wrote to the centre on WhatsApp. This is what the bell
--               is FOR - it is the only event in the system where a real person
--               is waiting for a human answer, and the only one where being told
--               late has a cost to somebody outside the building.
--   'whatsapp'  the platform reporting that one of the workspace's numbers
--               stopped working, and what its message types were moved to.
--
-- Everything else is dead. Deleting it is safe in a way that deleting almost
-- nothing else here would be: a notification is a notice that an event happened,
-- never the record OF the event. The number that went down is still on
-- whatsapp_instance; the message that was sent is still in wa_message_log. What
-- goes is the "you were told" copy, for tellings about features that are gone.
-- ---------------------------------------------------------------------------

delete from notifications
 where type is null
    or type not in ('chat', 'whatsapp');

-- "Mine, newest first" is already served by notifications_recipient_idx from
-- V25. The count beside the bell is not: it is polled by every signed-in tab
-- every thirty seconds, and it reads a predicate that index cannot satisfy. A
-- partial index over the unread rows alone is small - the whole point is that
-- almost nothing is in it.
create index if not exists notifications_unread_idx
    on notifications (recipient_user_id)
    where is_read = false;

comment on table notifications is
    'The in-app bell. Only two producers: an inbound WhatsApp message (chat), and a number going down (whatsapp).';
comment on column notifications.link_id is
    'What the row opens. For chat this is wa_conversation.id, which /messages?c= consumes.';
