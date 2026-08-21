package com.center.whatsapp.queue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.center.common.tenant.TenantContext;
import com.center.common.tenant.TenantScopedExecutor;
import com.center.messaging.service.WhatsappLogSender;
import com.center.whatsapp.cloud.service.WhatsappThrottle;
import com.center.whatsapp.quota.WhatsappQuotaService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spends the daily allowance, one message at a time, until it runs out.
 *
 * <p>This is the only place in the system that calls WhatsApp in bulk, and it is
 * built around one arithmetic fact: <b>Meta counts unique recipients, not
 * messages.</b> A parent already messaged inside the rolling 24-hour window
 * costs nothing to message again. In a school that is most of the roster, so a
 * drain that simply took {@code min(remaining, batch)} would refuse work it
 * could easily have done. Each row is therefore priced individually - free if
 * that phone is already inside the window, one unit of allowance if not.
 *
 * <p>Four ways a pass ends, and they are genuinely different:
 *
 * <ul>
 *   <li><b>Nothing due.</b> The common case; costs one index scan.</li>
 *   <li><b>Allowance spent.</b> Remaining rows are pushed to the moment the
 *       window next frees a recipient. Not retried sooner - there is nothing to
 *       gain and a rejection to lose.</li>
 *   <li><b>Meta said stop</b> (131048, 368, 80007, 190). The pass halts on the
 *       first one. Continuing is not merely wasted: for a quality restriction it
 *       is the behaviour being measured.</li>
 *   <li><b>Budget reached.</b> A pass has a wall-clock ceiling so the scheduler
 *       thread, which is shared, is never held for a whole broadcast.</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WhatsappSendQueueDrainJob {

    /** How many rows one pass will claim. Small: a pass should end, not finish. */
    private static final int CLAIM = 60;

    /** How long a claimed row may stay SENDING before another pass reclaims it. */
    private static final int LEASE_MINUTES = 5;

    /**
     * Attempts before a row is given up on.
     *
     * <p>Only transport failures and Meta's "try again" codes consume one. A
     * permanent refusal fails the row on its first attempt, and running out of
     * allowance consumes none at all - waiting is not failing.
     */
    private static final int MAX_ATTEMPTS = 8;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final WhatsappQuotaService quota;
    private final WhatsappThrottle throttle;
    private final WhatsappLogSender logSender;
    private final TenantScopedExecutor tenantTx;

    /**
     * One pass at a time, process-wide.
     *
     * <p>Spring's scheduler will not overlap a {@code fixedDelay} method with
     * itself, but the manual kick after an enqueue can land while a scheduled
     * pass is running. Two passes claiming rows concurrently would each see the
     * other's spend as unspent and together overshoot the ceiling.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${app.meta.drain-budget-seconds:45}")
    private long budgetSeconds;

    /** One claimed row, everything needed to send it. */
    private record Row(UUID id, UUID adminId, UUID batchId, String phone, String recipientName,
            String recipientCode, String recipientType, UUID studentId, String body,
            Map<String, String> vars, String source, String origin, UUID lectureId, UUID groupId,
            UUID sentByUserId, String sentByName, short attempts) {}

    @Scheduled(fixedDelayString = "${app.meta.drain-interval-ms:15000}", initialDelay = 30_000L)
    public void scheduled() {
        drain();
    }

    /**
     * Run a pass now.
     *
     * <p>Called straight after an enqueue so a batch that fits inside the
     * allowance starts moving in the same breath as the button press, rather
     * than after the next scheduled tick. Returns quietly when a pass is already
     * in flight.
     */
    public void drain() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            runPass();
        } catch (RuntimeException ex) {
            // A drain that throws is a drain that stops for good once the
            // scheduler gives up on it. Nothing here is worth that.
            log.error("WhatsApp queue drain failed", ex);
        } finally {
            running.set(false);
        }
    }

    private void runPass() {
        reclaimExpiredLeases();

        if (!throttle.pausedFor().isZero()) {
            // Meta has told us to stop. Do not even claim rows: a claim we
            // cannot act on is a row held hostage for the lease duration.
            return;
        }

        List<Row> rows = claim();
        if (rows.isEmpty()) {
            return;
        }

        WhatsappQuotaService.Quota q = quota.current();
        long budget = q.remaining();
        Set<String> free = new HashSet<>(quota.countedPhones(rows.stream().map(Row::phone).toList()));

        long deadline = System.nanoTime() + budgetSeconds * 1_000_000_000L;
        List<UUID> deferred = new ArrayList<>();
        boolean halted = false;

        for (Row row : rows) {
            if (halted || System.nanoTime() > deadline) {
                deferred.add(row.id());
                continue;
            }

            boolean costsAllowance = !free.contains(row.phone());
            if (costsAllowance && budget <= 0) {
                // Out of allowance. Everything left waits for the window to
                // roll - including the rows that would have been free, because
                // sending those out of order would scramble a lesson's roster.
                deferred.add(row.id());
                continue;
            }

            // No acquire() here: WhatsappLogSender paces every send at the one
            // funnel they all pass through, so doing it again would double the
            // gap between messages.
            WhatsappLogSender.Delivery result = deliver(row);

            if (result.sent()) {
                if (costsAllowance) {
                    budget--;
                    free.add(row.phone());
                }
                finish(row.id(), "SENT", null, null, result.logId());
                continue;
            }

            if (result.fatal()) {
                // The account itself is in trouble. Stop the pass, put this row
                // back, and let the throttle's pause keep the next pass away.
                log.error("Halting the WhatsApp drain: Meta code {} - {}",
                        result.errorCode(), result.failureReason());
                deferred.add(row.id());
                halted = true;
                continue;
            }

            if (result.retryable() || result.recipientBackoff() || result.errorCode() == null) {
                retry(row, result);
                continue;
            }

            // Anything else Meta named is permanent: not on WhatsApp, opted out,
            // dropped by pacing. Retrying it is what distorts delivery metrics.
            finish(row.id(), "FAILED", result.errorCode(), result.failureReason(), result.logId());
        }

        if (!deferred.isEmpty()) {
            defer(deferred, q.nextFreeAt());
        }
    }

    /**
     * Send one row inside its own workspace.
     *
     * <p>{@code wa_message_log} is tenant-scoped by Hibernate, and this runs on
     * a scheduler thread with no logged-in user. Binding the tenant BEFORE the
     * transaction opens is the whole point of {@link TenantScopedExecutor} -
     * Hibernate resolves the tenant once, when the session opens, and binding it
     * afterwards is too late.
     */
    private WhatsappLogSender.Delivery deliver(Row row) {
        try {
            return TenantContext.callAs(row.adminId(), () -> tenantTx.inTenantTx(() ->
                    logSender.send(
                            new WhatsappLogSender.Recipient(row.recipientName(), row.phone(),
                                    row.recipientCode(), row.recipientType(), row.studentId()),
                            row.body(), row.source(), row.origin(), row.lectureId(), row.groupId(),
                            row.sentByUserId(), row.sentByName(), row.vars(), row.batchId())));
        } catch (RuntimeException ex) {
            log.warn("WhatsApp send threw for queue row {}: {}", row.id(), ex.getMessage());
            return new WhatsappLogSender.Delivery(false, ex.getMessage(), null, null);
        }
    }

    // ── row state ───────────────────────────────────────────────────────────

    /**
     * Take up to {@link #CLAIM} due rows.
     *
     * <p>{@code for update skip locked} rather than a read-then-update: two
     * passes must never claim the same row, and skipping locked rows means a
     * slow pass does not block a fast one. Ordered by due time then batch
     * position so a lesson leaves in roster order.
     *
     * <p>Un-tenanted on purpose. The allowance is shared platform-wide, so the
     * queue has to be drained globally or one workspace could starve another by
     * simply queueing first every morning.
     *
     * <p>No {@code @Transactional}. It would be a lie here - this is called from
     * a sibling method on the same bean, so the proxy is never involved - and it
     * is also unnecessary: the whole claim is ONE statement, and one statement is
     * already atomic. Annotating it would suggest a guarantee that came from
     * somewhere it did not.
     */
    private List<Row> claim() {
        List<Row> rows = jdbc.query("""
                update wa_send_queue q
                   set state = 'SENDING',
                       leased_until = now() + interval '%d minutes',
                       attempts = attempts + 1
                 where q.id in (
                       select id from wa_send_queue
                        where state = 'PENDING' and next_attempt_at <= now()
                        order by next_attempt_at, seq
                        limit %d
                        for update skip locked)
             returning q.id, q.admin_id, q.batch_id, q.phone, q.recipient_name, q.recipient_code,
                       q.recipient_type, q.student_id, q.body, q.vars, q.source, q.origin,
                       q.lecture_id, q.group_id, q.sent_by_user_id, q.sent_by_name, q.attempts
                """.formatted(LEASE_MINUTES, CLAIM),
                (rs, n) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getObject("admin_id", UUID.class),
                        rs.getObject("batch_id", UUID.class),
                        rs.getString("phone"),
                        rs.getString("recipient_name"),
                        rs.getString("recipient_code"),
                        rs.getString("recipient_type"),
                        rs.getObject("student_id", UUID.class),
                        rs.getString("body"),
                        vars(rs.getString("vars")),
                        rs.getString("source"),
                        rs.getString("origin"),
                        rs.getObject("lecture_id", UUID.class),
                        rs.getObject("group_id", UUID.class),
                        rs.getObject("sent_by_user_id", UUID.class),
                        rs.getString("sent_by_name"),
                        rs.getShort("attempts")));
        return rows;
    }

    private void finish(UUID id, String state, Integer code, String reason, UUID logId) {
        jdbc.update("""
                update wa_send_queue
                   set state = ?, failure_code = ?, failure_reason = ?, log_id = ?,
                       leased_until = null, finished_at = now()
                 where id = ?
                """, state, code, reason, logId, id);
    }

    /**
     * Put a row back with a growing delay.
     *
     * <p>1, 2, 4, 8... minutes, capped at half an hour. The cap matters: a
     * recipient-level backoff that grew without bound would push a single
     * parent's message a day into the future over a handful of retries.
     */
    private void retry(Row row, WhatsappLogSender.Delivery result) {
        if (row.attempts() >= MAX_ATTEMPTS) {
            finish(row.id(), "FAILED", result.errorCode(),
                    result.failureReason() == null
                            ? "تعذّر الإرسال بعد عدة محاولات"
                            : result.failureReason(),
                    result.logId());
            return;
        }
        long minutes = Math.min(30L, 1L << Math.min(5, Math.max(0, row.attempts() - 1)));
        // The pair limit is a six-second rule, not a minute-scale one; retrying
        // that recipient in a minute is right, and waiting half an hour is not.
        if (result.recipientBackoff()) {
            minutes = 1;
        }
        jdbc.update("""
                update wa_send_queue
                   set state = 'PENDING',
                       next_attempt_at = now() + (? * interval '1 minute'),
                       failure_code = ?, failure_reason = ?, leased_until = null
                 where id = ?
                """, (int) minutes, result.errorCode(), result.failureReason(), row.id());
    }

    /**
     * Hand rows back untouched because there was no allowance to spend on them.
     *
     * <p>The attempt is given back too. Waiting for a quota is not a failed
     * attempt, and counting it as one would exhaust {@link #MAX_ATTEMPTS} on a
     * roster that never had anything wrong with it.
     */
    private void defer(List<UUID> ids, OffsetDateTime nextFreeAt) {
        OffsetDateTime when = nextFreeAt == null
                ? OffsetDateTime.now().plusMinutes(5)
                : nextFreeAt.plusSeconds(30);
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] args = new Object[ids.size() + 1];
        args[0] = when;
        for (int i = 0; i < ids.size(); i++) {
            args[i + 1] = ids.get(i);
        }
        jdbc.update("""
                update wa_send_queue
                   set state = 'PENDING',
                       next_attempt_at = greatest(?::timestamptz, now() + interval '30 seconds'),
                       attempts = greatest(0, attempts - 1),
                       leased_until = null
                 where id in (%s)
                """.formatted(placeholders), args);
    }

    /**
     * Rows whose sender died mid-flight.
     *
     * <p>They keep their incremented attempt count: the send may well have
     * reached Meta before the process went, so treating the reclaim as free
     * would let a crash loop send the same message repeatedly.
     */
    private void reclaimExpiredLeases() {
        int n = jdbc.update("""
                update wa_send_queue
                   set state = 'PENDING', leased_until = null,
                       next_attempt_at = now()
                 where state = 'SENDING' and leased_until < now()
                """);
        if (n > 0) {
            log.warn("Reclaimed {} WhatsApp queue rows abandoned mid-send", n);
        }
    }

    private Map<String, String> vars(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception ex) {
            return null;
        }
    }
}
