-- Whether a student's barcode card has ever reached them.
--
-- The fact was already in the system, but only in wa_message_log - an
-- append-only history of every message the workspace ever attempted. That is the
-- wrong shape for a question asked once per row while a table is drawn, and it
-- cannot answer "everyone who never got one" without a full scan and a group-by.
-- Stamping the student is what makes the resend button able to skip whoever
-- already has their card, which is the whole point of it.

alter table students
    add column if not exists barcode_sent_at timestamptz;

comment on column students.barcode_sent_at is
    'When this student''s barcode card was first delivered. Null = never sent.';

-- Backfill from the send log, so the button starts out knowing what the history
-- already knew. Both origins carry the same card: NEW_STUDENT is the welcome
-- fired the moment the student was created, BARCODE is the button pressed
-- afterwards (and the older direct-document send, which logged that same
-- origin). Only delivered rows count - a failed attempt never reached anyone -
-- and the EARLIEST is kept, because the column records when they first got it.
update students s
set barcode_sent_at = l.first_sent
from (
        select student_id, min(created_at) as first_sent
        from wa_message_log
        where student_id is not null
          and status = 'SENT'
          and origin in ('NEW_STUDENT', 'BARCODE')
        group by student_id
    ) l
where l.student_id = s.id
  and s.barcode_sent_at is null;

-- The pending set is exactly what the resend button reads, and it shrinks
-- towards empty as the cards go out. A partial index covers that set and costs
-- almost nothing once it is empty - which is the steady state.
create index if not exists students_barcode_pending_idx
    on students (admin_id)
    where barcode_sent_at is null;
