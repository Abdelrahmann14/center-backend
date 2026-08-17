-- Groups are now soft-deleted, not removed.
--
-- A hard delete cascaded away attendance/finance/lesson history and null-ed the
-- group off past registrations, losing the label their records showed. Deleting a
-- group now sets `deleted = true` (and is_active=false): the row stays so past
-- registrations/attendance still resolve its label, but it disappears from every
-- forward-looking picker, and its students are transferred to another group first.

alter table groups add column deleted boolean not null default false;
