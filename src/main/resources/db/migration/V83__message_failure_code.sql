-- Meta's numeric error code on a failed delivery, kept beside the sentence we
-- already store.
--
-- The reason text is written for a human to read and Meta rewords it freely; the
-- code is stable and is the only thing that can be branched on. One code matters
-- above the rest: 131026, "message undeliverable", which is what a number that
-- is not on WhatsApp produces. That is as close as the official API gets to the
-- number check the platform used to do - there is no endpoint that answers "is
-- this number on WhatsApp", so the answer has to be learned from what actually
-- happened to the messages already sent.
alter table wa_message_log
    add column if not exists failure_code integer;

comment on column wa_message_log.failure_code is
    'Meta error code on a failed send (131026 = undeliverable). Null when it went.';

-- Reachability is read per workspace, newest row first, one phone at a time.
-- The existing (admin_id, created_at) index cannot serve that without scanning
-- the workspace's whole history and sorting it.
create index if not exists wa_message_log_admin_phone_idx
    on wa_message_log (admin_id, phone, created_at desc);
