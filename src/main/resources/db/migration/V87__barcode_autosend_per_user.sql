-- The "send the card automatically" switch moves from the workspace to the
-- account.
--
-- V86 put it on the NEW_STUDENT automation, which is tenant-scoped: one teacher,
-- one answer, shared by every assistant working under them. That is wrong for
-- how the desk actually runs. The assistant entering a walk-in wants the card to
-- leave with the student; the one importing last year's roster does not, and
-- neither should have to reach across and change the other's setting - or
-- discover mid-import that somebody already did.
--
-- So each account carries its own, and a student's card is decided by whoever
-- entered them. Off by default, so a new account never sends anything until its
-- owner turns it on.
alter table users
    add column if not exists barcode_auto_send boolean not null default false;

comment on column users.barcode_auto_send is
    'Does adding a student from this account send them their barcode card at once. '
    'Per account, not per workspace: assistants under one teacher decide separately.';

-- Undo V86. The automation''s enabled flag is no longer the switch - the account
-- column above is - so leaving it false would veto every send regardless of what
-- anybody turned on, from a row no screen shows.
update wa_message_automation
set enabled = true
where type = 'NEW_STUDENT'
  and not enabled;
