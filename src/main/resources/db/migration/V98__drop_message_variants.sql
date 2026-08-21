-- ---------------------------------------------------------------------------
-- The history stops keeping its own copy of every message.
--
-- `wa_message_variant` held the text of each automated message: one row per
-- wording, seeded from a hardcoded default in WhatsappMessagingService, editable
-- from a page that was removed when the system moved to the WhatsApp Cloud API.
--
-- From that move onwards it was recording a lie. What WhatsApp delivers is an
-- APPROVED TEMPLATE - Meta will carry nothing else for a business-initiated
-- message - and these rows were never the template. They were a copy of what the
-- template said when somebody last typed it out, kept in a different place, with
-- nothing to keep the two in step. Editing a template in WhatsApp Manager did
-- not touch them. So the teacher read one wording in the log while the parent's
-- phone showed another, and neither of them could tell which was which. The rows
-- surviving here are Green API-era wordings, from before the Cloud API existed.
--
-- Worse, they were load-bearing: `requireVariants` refused to send when the text
-- was blank, so a dead copy of a message was gating a live one.
--
-- The log now records the template's own words, filled at send time from the
-- same values the send fills its numbered placeholders from - so the history and
-- the phone cannot disagree, because they are rendered from one source.
--
-- Nothing else referenced this table: the code that read it is gone, no foreign
-- key points at it, and wa_message_automation (the on/off switch, the audience,
-- the absence week range) is untouched and still very much in use.
-- ---------------------------------------------------------------------------

drop table if exists wa_message_variant;

comment on table wa_message_automation is
    'Whether a message type is live and who it goes to. The WORDING belongs to the approved Meta template.';
