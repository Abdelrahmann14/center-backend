-- Remove the {exam.bonus} variable.
--
-- It was a slot that sat immediately after the score and was empty for almost
-- every exam - one more thing an author had to remember, place correctly, and
-- leave a stray space behind when it rendered blank. The bonus itself is not
-- lost: it is now appended to {exam.score}, so "18 (+2 بونص)" arrives exactly as
-- it did before, from one variable instead of two.
--
-- Stored bodies have to be rewritten here, not just left alone. A token that is
-- no longer in the catalog stops being substituted, and the renderer turns any
-- leftover {x} into WhatsApp *x* bold - so an untouched template would have
-- started sending parents a literal "*exam.bonus*".

update message_templates
   set body = replace(body, '{exam.bonus}', '')
 where body like '%{exam.bonus}%';

update message_templates
   set variables = replace(replace(variables, ',exam.bonus', ''), 'exam.bonus,', '')
 where variables like '%exam.bonus%';

update wa_message_variant
   set body = replace(body, '{exam.bonus}', '')
 where body like '%{exam.bonus}%';

-- Already-sent rows (notifications, wa_message_log) are history and are left
-- exactly as they went out.
