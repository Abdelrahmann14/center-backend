-- Remove Green API.
--
-- The centre now sends only through the official WhatsApp Business account
-- hosted by Meta, so there is no second provider to distinguish, no per-number
-- credential to store, and no phone logged into the WhatsApp app to keep alive.
--
-- What that costs, and why each is unavoidable rather than a choice:
--
--   * The WhatsApp-presence cache (whatsapp_numbers) goes. "Is this number on
--     WhatsApp?" was answered by Green API's checkWhatsapp; the official API has
--     no equivalent call, so the answer cannot be obtained at all any more.
--
--   * send_as_image goes. It rendered a message to a PNG and uploaded it to the
--     chat. The official API will not carry a picture the business sends
--     unprompted unless it is an approved template's own header, and such a
--     template still delivers its reviewed TEXT - which is the thing the picture
--     was standing in for.
--
-- History is kept. wa_message_log rows written while Green was in use stay
-- exactly as they are; only the column naming which service carried them is
-- dropped, since there is now only one answer.

-- ---------------------------------------------------------------------------
-- 1. Green numbers themselves.
--
-- A row with no phone_number_id was a Green instance and can no longer send
-- anything. Its message-type assignments cascade off it (whatsapp_responsibility
-- references whatsapp_instance ON DELETE CASCADE), which is what we want: a type
-- with no row falls back to the first working number, so the fallback answers
-- for it rather than a dangling id failing one parent at a time.
-- ---------------------------------------------------------------------------
delete from whatsapp_instance where phone_number_id is null;

drop index if exists whatsapp_instance_instid_key;

alter table whatsapp_instance
    drop column if exists provider,
    drop column if exists instance_id,
    drop column if exists api_token,
    drop column if exists base_url;

-- Every remaining row is addressed by Meta's id, and a row without one could not
-- be sent through. The unique partial index from V76 is replaced by a plain one,
-- since the column can no longer be null.
alter table whatsapp_instance alter column phone_number_id set not null;
drop index if exists whatsapp_instance_phone_number_id_key;
create unique index whatsapp_instance_phone_number_id_key
    on whatsapp_instance (phone_number_id);

-- ---------------------------------------------------------------------------
-- 2. The WhatsApp-presence cache and everything that fed it.
-- ---------------------------------------------------------------------------
drop index if exists whatsapp_numbers_pending_idx;
drop index if exists whatsapp_numbers_key;
drop table if exists whatsapp_numbers cascade;

-- ---------------------------------------------------------------------------
-- 3. The message log's provider column.
--
-- The cost report used to select on provider = 'CLOUD_API'; it now selects on
-- template_category being present, which is the same set of rows (only a
-- template send carries a category) and stays correct for the Green-era history
-- this table keeps.
-- ---------------------------------------------------------------------------
alter table wa_message_log drop column if exists provider;

-- ---------------------------------------------------------------------------
-- 4. Send-as-image, and a template column nothing ever read.
--
-- cloud_template_name was added in V76 to name an automation's template; the
-- mapping landed on wa_type_template instead (V77), keyed by message type, and
-- this column has been dead since.
-- ---------------------------------------------------------------------------
alter table wa_message_automation drop column if exists send_as_image;
alter table wa_message_variant drop column if exists send_as_image;
alter table wa_message_automation drop column if exists cloud_template_name;
