package com.center.whatsapp.queue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.tenant.TenantContext;
import com.center.whatsapp.quota.WhatsappQuotaService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Where a send button puts its work.
 *
 * <p>A button used to loop over the roster and make one blocking HTTP call per
 * recipient, on the request thread, with no cap. Three things were wrong with
 * that and only the third is obvious:
 *
 * <ol>
 *   <li>A five-minute run outlives the browser's connection. The teacher saw a
 *       network error and never learned how many messages went.</li>
 *   <li>It could not stop at Meta's daily ceiling, because nothing counted it.
 *       Past the limit every remaining call was rejected and the loop carried
 *       on - hundreds of guaranteed rejections, which is itself the behaviour
 *       that damages a number's quality rating.</li>
 *   <li>Whatever did not go was simply lost. There was nothing to resume.</li>
 * </ol>
 *
 * <p>So the button writes rows and returns. {@link WhatsappSendQueueDrainJob}
 * spends the allowance as it becomes available, and a lesson of a hundred with
 * forty-five left goes out as forty-five now and fifty-five when the rolling
 * window frees them - without anyone pressing anything again.
 *
 * <p>The message body is rendered at enqueue time, not at send time. A message
 * that leaves four hours late must say what it would have said when the teacher
 * pressed the button; re-rendering it later would quietly describe a lesson that
 * had since been edited.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsappSendQueue {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final WhatsappQuotaService quota;

    /** One rendered message, waiting for allowance. */
    public record Pending(String phone, String recipientName, String recipientCode,
            String recipientType, UUID studentId, String body, Map<String, String> vars,
            String source, String origin, UUID lectureId, UUID groupId,
            UUID sentByUserId, String sentByName) {}

    /**
     * What one press produced.
     *
     * @param queued      rows written and now owed to somebody
     * @param duplicate   skipped because this student was already owed, or had
     *                    already been sent, this exact message for this lesson
     * @param sendableNow how many of the queued rows the current allowance can
     *                    pay for immediately - the honest answer to "هيتبعت كام
     *                    دلوقتي؟"
     * @param waiting     the rest, which go when the window rolls
     * @param nextFreeAt  when the first of the waiting ones becomes payable
     */
    public record Enqueued(UUID batchId, int queued, int duplicate, int sendableNow, int waiting,
            OffsetDateTime nextFreeAt, long remaining, int tier, boolean blocked,
            String blockedReason) {}

    /** Progress of one batch, for the page that started it. */
    public record Progress(UUID batchId, long pending, long sending, long sent, long failed,
            long cancelled, long total, boolean done) {}

    // ── enqueueing ──────────────────────────────────────────────────────────

    /**
     * Write a batch. Returns immediately; nothing has been sent yet.
     *
     * <p>Duplicates are dropped by the database, not by a read-then-write check
     * here: two assistants pressing the same button in the same second must not
     * both see an empty queue and both fill it. {@code wa_send_queue_once_idx}
     * makes the second insert a no-op.
     */
    @Transactional
    public Enqueued enqueue(List<Pending> messages) {
        UUID adminId = TenantContext.get();
        UUID batchId = UUID.randomUUID();
        if (adminId == null) {
            throw new IllegalStateException("No workspace bound - cannot queue WhatsApp messages");
        }
        if (messages == null || messages.isEmpty()) {
            return new Enqueued(batchId, 0, 0, 0, 0, null, 0, 0, false, null);
        }

        int written = 0;
        int seq = 0;
        for (Pending m : messages) {
            if (m.phone() == null || m.phone().isBlank()) {
                continue;
            }
            written += jdbc.update("""
                    insert into wa_send_queue (
                        admin_id, batch_id, seq, phone, recipient_name, recipient_code,
                        recipient_type, student_id, body, vars, source, origin,
                        lecture_id, group_id, sent_by_user_id, sent_by_name)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                    on conflict do nothing
                    """,
                    adminId, batchId, seq++, m.phone(), m.recipientName(), m.recipientCode(),
                    m.recipientType(), m.studentId(), m.body(), json(m.vars()), m.source(),
                    m.origin(), m.lectureId(), m.groupId(), m.sentByUserId(), m.sentByName());
        }

        int duplicate = messages.size() - written;
        WhatsappQuotaService.Quota q = quota.current();

        // How many of these the allowance actually covers. Not min(remaining,
        // written): Meta counts unique PEOPLE, so a parent already messaged in
        // the last 24 hours costs nothing to message again - and in a school
        // that is most of them. A lesson whose parents all got the attendance
        // message this morning consumes zero allowance for the absence message
        // this afternoon, and reporting otherwise would look like a refusal.
        List<String> phones = messages.stream().map(Pending::phone).filter(p -> p != null).toList();
        var alreadyCounted = quota.countedPhones(phones);
        int costly = (int) phones.stream().distinct().filter(p -> !alreadyCounted.contains(p))
                .count();
        int payable = (int) Math.min(costly, q.remaining());
        int sendableNow = written - (costly - payable);
        int waiting = written - sendableNow;

        log.info("Queued {} WhatsApp messages ({} duplicates skipped); {} payable now, {} waiting",
                written, duplicate, sendableNow, waiting);

        return new Enqueued(batchId, written, duplicate, Math.max(0, sendableNow),
                Math.max(0, waiting), waiting > 0 ? q.nextFreeAt() : null, q.remaining(), q.tier(),
                q.exhausted() && waiting > 0, q.exhausted() ? blockedReason(q) : null);
    }

    private static String blockedReason(WhatsappQuotaService.Quota q) {
        if ("RESTRICTED".equalsIgnoreCase(q.numberStatus())
                || "BANNED".equalsIgnoreCase(q.numberStatus())) {
            return "رقم واتساب مقيّد من ميتا حاليًا — راجع حالة الرقم";
        }
        return "تم استهلاك حصة اليوم (" + q.used() + " من " + q.tier()
                + "). الرسائل المتبقية هتتبعت تلقائيًا لما الحصة تتجدد.";
    }

    // ── reading ─────────────────────────────────────────────────────────────

    public Progress progress(UUID batchId) {
        UUID adminId = TenantContext.get();
        Map<String, Long> byState = new java.util.HashMap<>();
        jdbc.query("""
                select state, count(*) as n from wa_send_queue
                 where admin_id = ? and batch_id = ? group by state
                """, rs -> {
            byState.put(rs.getString("state"), rs.getLong("n"));
        }, adminId, batchId);
        long pending = byState.getOrDefault("PENDING", 0L);
        long sending = byState.getOrDefault("SENDING", 0L);
        long sent = byState.getOrDefault("SENT", 0L);
        long failed = byState.getOrDefault("FAILED", 0L);
        long cancelled = byState.getOrDefault("CANCELLED", 0L);
        long total = pending + sending + sent + failed + cancelled;
        return new Progress(batchId, pending, sending, sent, failed, cancelled, total,
                pending == 0 && sending == 0);
    }

    /** Still owed for this lesson and kind - what the button badge counts down. */
    public long waitingFor(UUID lectureId, String origin) {
        UUID adminId = TenantContext.get();
        Long n = jdbc.queryForObject("""
                select count(*) from wa_send_queue
                 where admin_id = ? and lecture_id = ? and origin = ?
                   and state in ('PENDING', 'SENDING')
                """, Long.class, adminId, lectureId, origin);
        return n == null ? 0L : n;
    }

    /** Everything this workspace is still waiting on, across all batches. */
    public long waitingTotal() {
        UUID adminId = TenantContext.get();
        Long n = jdbc.queryForObject("""
                select count(*) from wa_send_queue
                 where admin_id = ? and state in ('PENDING', 'SENDING')
                """, Long.class, adminId);
        return n == null ? 0L : n;
    }

    /** Students of this lesson already queued or sent, so planning can skip them. */
    public List<UUID> queuedStudentIds(UUID lectureId, String origin) {
        UUID adminId = TenantContext.get();
        List<UUID> out = new ArrayList<>();
        jdbc.query("""
                select distinct student_id from wa_send_queue
                 where admin_id = ? and lecture_id = ? and origin = ?
                   and student_id is not null
                   and state in ('PENDING', 'SENDING', 'SENT')
                """, rs -> {
            out.add(rs.getObject(1, UUID.class));
        }, adminId, lectureId, origin);
        return out;
    }

    // ── calling it off ──────────────────────────────────────────────────────

    /**
     * Stop whatever has not gone yet.
     *
     * <p>Only PENDING rows. A row that is SENDING is mid-flight at Meta and
     * cancelling it here would produce a message that was delivered and recorded
     * as cancelled - the one state that is a lie.
     */
    @Transactional
    public int cancel(UUID batchId) {
        UUID adminId = TenantContext.get();
        return jdbc.update("""
                update wa_send_queue
                   set state = 'CANCELLED', finished_at = now()
                 where admin_id = ? and batch_id = ? and state = 'PENDING'
                """, adminId, batchId);
    }

    private String json(Map<String, String> vars) {
        if (vars == null || vars.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(vars);
        } catch (Exception ex) {
            // A message that loses its variables sends with empty placeholders,
            // which Meta rejects outright - better to record none and have the
            // template resolver refuse it clearly.
            log.warn("Could not serialise message variables: {}", ex.getMessage());
            return null;
        }
    }
}
