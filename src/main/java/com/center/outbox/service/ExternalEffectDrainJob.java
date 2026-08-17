package com.center.outbox.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.center.common.tenant.TenantContext;
import com.center.google.service.GoogleContactSyncService;
import com.center.messaging.service.WhatsappMessagingService;
import com.center.outbox.entity.ExternalEffect;
import com.center.outbox.repository.ExternalEffectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The read side of the external-effect queue: every 30 seconds it takes whatever
 * is due and tries to push it out to Google / WhatsApp.
 *
 * <p>This is what makes "the internet came back" a thing the system notices. An
 * effect queued while the line was down sits here doing nothing expensive, and
 * the first pass after the line returns completes it - without anybody pressing
 * anything.
 *
 * <p>A failure is not a loss: the row stays, its attempt count grows and its next
 * attempt is pushed out with exponential backoff (capped, so a long outage still
 * retries twice an hour rather than once a week). Only a genuinely dead effect -
 * one whose subject no longer exists, or which has failed for a full day - is
 * dropped, and it says so in the log.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalEffectDrainJob {

    /** How many due effects one pass takes. Keeps a backlog from hogging a tick. */
    private static final int BATCH = 50;

    /** Never wait longer than this between attempts, however long the outage. */
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

    /** After this many failures the effect is abandoned (about a day of retries). */
    private static final int MAX_ATTEMPTS = 60;

    /**
     * Longest one pass may keep starting new effects.
     *
     * <p>The batch size alone bounded nothing. A single WHATSAPP_LECTURE effect
     * replays a whole lesson's messages - one HTTP call per recipient, so a
     * 200-student lesson is 200 sequential calls - and fifty of those in one
     * batch could occupy the pass for hours. Everything else queued behind them,
     * every Google contact included, waited that long too: the drainer starving
     * itself.
     *
     * <p>The check sits between effects, never inside one, so nothing is ever
     * left half-applied. Whatever is skipped is simply still due, and the next
     * tick is thirty seconds away.
     */
    private static final Duration PASS_BUDGET = Duration.ofMinutes(5);

    private final ExternalEffectRepository repository;
    private final GoogleContactSyncService googleSync;
    private final WhatsappMessagingService messaging;
    private final ObjectMapper objectMapper;

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "PT30S")
    public void drain() {
        List<ExternalEffect> due;
        try {
            due = repository.findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                    OffsetDateTime.now(), Limit.of(BATCH));
        } catch (org.springframework.dao.DataAccessException ex) {
            // The database is unreachable - which is the very situation this queue
            // exists for. Nothing is lost: the rows are still there and the next
            // tick tries again. One line, because the scheduler's default handler
            // prints the same full stack every 30 seconds for the whole outage.
            log.warn("outbox: skipping this pass, database unreachable");
            return;
        }
        long deadline = System.nanoTime() + PASS_BUDGET.toNanos();
        int done = 0;
        for (ExternalEffect effect : due) {
            if (System.nanoTime() > deadline) {
                log.info("outbox: pass budget spent after {} of {} due effect(s); the rest stay queued",
                        done, due.size());
                return;
            }
            try {
                run(effect);
                repository.delete(effect);
            } catch (RuntimeException ex) {
                fail(effect, ex);
            }
            done++;
        }
    }

    /** Dispatch one effect. Throwing means "not done - try again later". */
    private void run(ExternalEffect effect) {
        switch (effect.getKind()) {
            case ExternalEffect.GOOGLE_CONTACT ->
                    googleSync.syncStudentThrowing(effect.getAdminId(), effect.getRefId());
            case ExternalEffect.WHATSAPP_LECTURE -> sendLecture(effect);
            default -> log.warn("outbox: unknown effect kind '{}' - dropping", effect.getKind());
        }
    }

    /**
     * Replay a lesson's attendance / absence send that was triggered while the
     * device was offline. The service skips anyone already messaged for this
     * lesson, so a replay can never double-send.
     */
    private void sendLecture(ExternalEffect effect) {
        JsonNode p = payload(effect);
        UUID lectureId = uuid(p.path("lecture_id").asText(null));
        UUID groupId = uuid(p.path("group_id").asText(null));
        String origin = p.path("origin").asText("ATTENDANCE");
        UUID byUser = uuid(p.path("by_user").asText(null));
        String byName = p.path("by_name").isMissingNode() ? null : p.path("by_name").asText(null);
        if (lectureId == null || groupId == null) {
            log.warn("outbox: WHATSAPP_LECTURE {} has no lesson/group - dropping", effect.getId());
            return;
        }
        TenantContext.callAs(effect.getAdminId(), () -> {
            if ("ABSENCE".equals(origin)) {
                messaging.sendLectureAbsence(lectureId, groupId, byUser, byName);
            } else {
                messaging.sendLectureAttendance(lectureId, groupId, byUser, byName);
            }
            return null;
        });
    }

    private JsonNode payload(ExternalEffect effect) {
        try {
            return effect.getPayload() == null
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(effect.getPayload());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void fail(ExternalEffect effect, RuntimeException ex) {
        int attempts = effect.getAttempts() + 1;
        if (attempts >= MAX_ATTEMPTS) {
            log.error("outbox: giving up on {} for {} after {} attempts: {}",
                    effect.getKind(), effect.getRefId(), attempts, ex.getMessage());
            repository.delete(effect);
            return;
        }
        // 1, 2, 4, 8 ... minutes, capped. Long enough to ride out an outage
        // without hammering a service that has already said no.
        long minutes = Math.min(MAX_BACKOFF.toMinutes(), 1L << Math.min(attempts, 5));
        effect.setAttempts(attempts);
        effect.setLastError(ex.getMessage());
        effect.setNextAttemptAt(OffsetDateTime.now().plusMinutes(minutes));
        repository.save(effect);
        log.debug("outbox: {} for {} deferred {}m ({})",
                effect.getKind(), effect.getRefId(), minutes, ex.getMessage());
    }
}
