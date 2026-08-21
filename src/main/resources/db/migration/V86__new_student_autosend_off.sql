-- The new-student welcome (which carries the barcode card) stops being
-- unconditional.
--
-- It used to fire on every single student the moment their row committed, with
-- no way to stop it short of clearing the template text. That is the wrong
-- default for the case it actually gets used in: importing a roster, or entering
-- a class in one sitting, sent a card - and a billed message - to every one of
-- them as fast as the rows were typed.
--
-- So the automation's `enabled` flag becomes the switch, off to begin with, and
-- the student form carries it beside the code. Existing workspaces are turned
-- off here rather than left on, because the whole point is that the send is now
-- something somebody chose. Nothing is lost: every student not sent to stays in
-- the barcode backlog, and the "إرسال الباركود" button on the students page
-- covers them whenever the teacher wants.
update wa_message_automation
set enabled = false
where type = 'NEW_STUDENT'
  and enabled;
