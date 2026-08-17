-- "Send as image" is now per-message wording, not per automation.
--
-- Each variant (the base + every AI alternative) carries its own flag, so when a
-- variant is picked at random for a recipient it is sent as text or image by that
-- variant's own setting. Seed each variant from its automation's old flag so an
-- existing choice is preserved.

alter table wa_message_variant add column send_as_image boolean not null default false;

update wa_message_variant v
   set send_as_image = a.send_as_image
  from wa_message_automation a
 where v.automation_id = a.id;
