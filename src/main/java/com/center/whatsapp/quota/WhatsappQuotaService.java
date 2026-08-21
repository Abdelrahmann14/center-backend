package com.center.whatsapp.quota;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.config.ApplicationProperties;
import com.center.whatsapp.cloud.service.CloudApiClient;
import com.center.whatsapp.entity.WhatsappInstance;
import com.center.whatsapp.repository.WhatsappInstanceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * How many more people this platform may message today.
 *
 * <p>Meta caps a business at N unique recipients per rolling 24 hours. Two facts
 * about that cap decide everything here, and both are easy to get wrong:
 *
 * <ol>
 *   <li><b>It belongs to the business portfolio, not to a phone number.</b> Meta
 *       moved it on 2025-10-07. Every teacher's number lives in the same
 *       portfolio, so they share one allowance - adding numbers buys throughput
 *       and not quota. Every query in this class is therefore deliberately
 *       <em>un-tenanted</em>: a per-workspace count would report a comfortable
 *       number while the real ceiling was already spent by somebody else.</li>
 *   <li><b>Meta publishes the limit but not the consumption.</b> There is no
 *       field on any node that answers "how many are left". The tier is read
 *       from Meta; the spend has to be counted here.</li>
 * </ol>
 *
 * <p>The count is of messages this system <em>accepted</em>, while Meta counts
 * messages it <em>delivered</em>. The two differ by whatever is in flight, which
 * is why {@code safety_margin} exists: the last few recipients of an allowance
 * are never spent on an estimate.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsappQuotaService {

    /**
     * Origins that do not count against the messaging limit.
     *
     * <p>A free-form reply inside a customer's own 24-hour window is a "service"
     * message: Meta charges nothing for it and counts it against nothing. Billing
     * it to the daily allowance would make the inbox eat the broadcast budget.
     */
    private static final List<String> UNCOUNTED_ORIGINS = List.of("INBOX");

    private final JdbcTemplate jdbc;
    private final CloudApiClient cloud;
    private final WhatsappInstanceRepository instances;
    private final ApplicationProperties properties;

    /**
     * The allowance right now.
     *
     * @param tier        what Meta says the ceiling is
     * @param used        unique recipients accepted in the last rolling 24 hours,
     *                    across every workspace
     * @param margin      recipients held back against the estimate being wrong
     * @param remaining   what a send may actually spend: never negative
     * @param queued      messages already waiting for allowance
     * @param nextFreeAt  when the oldest counted recipient falls out of the
     *                    rolling window and one more becomes available. Null when
     *                    nothing is spent. This is what "الحصة بتتجدد إمتى"
     *                    means - the window rolls continuously, it does not reset
     *                    at midnight
     * @param stale       true when the tier has never been read from Meta, or was
     *                    read too long ago to be trusted
     */
    public record Quota(int tier, String tierLabel, long used, int margin, long remaining,
            long queued, OffsetDateTime nextFreeAt, boolean stale, String qualityRating,
            String numberStatus, OffsetDateTime refreshedAt) {

        /** Nothing may go out at all - either spent, or the account is blocked. */
        public boolean exhausted() {
            return remaining <= 0;
        }
    }

    /** The tier row, as stored. */
    private record Stored(int tier, String tierLabel, int margin, String qualityRating,
            String numberStatus, OffsetDateTime refreshedAt) {}

    // ── reading ─────────────────────────────────────────────────────────────

    public Quota current() {
        Stored stored = stored();
        long used = usedLast24h();
        long queued = queuedCount();
        long remaining = Math.max(0L, (long) stored.tier() - stored.margin() - used);
        boolean stale = stored.refreshedAt() == null
                || stored.refreshedAt().isBefore(OffsetDateTime.now().minusHours(24));
        return new Quota(stored.tier(), stored.tierLabel(), used, stored.margin(), remaining,
                queued, nextFreeAt(), stale, stored.qualityRating(), stored.numberStatus(),
                stored.refreshedAt());
    }

    /**
     * Unique recipients accepted platform-wide in the last rolling 24 hours.
     *
     * <p>{@code distinct phone} because Meta counts <em>people</em>, not
     * messages: a parent who was told about attendance and then about a grade is
     * one recipient, not two. {@code status = 'SENT'} because a rejected message
     * reached nobody and costs nothing.
     *
     * <p>No {@code admin_id} predicate. That is not an omission - see the class
     * note.
     */
    public long usedLast24h() {
        Long count = jdbc.queryForObject("""
                select count(distinct phone)
                  from wa_message_log
                 where status = 'SENT'
                   and created_at > now() - interval '24 hours'
                   and phone is not null
                   and origin <> all (?)
                """, Long.class, (Object) UNCOUNTED_ORIGINS.toArray(String[]::new));
        return count == null ? 0L : count;
    }

    /**
     * When one more recipient frees up.
     *
     * <p>The window is a moving 24 hours, not a calendar day: the allowance
     * spent at 14:00 yesterday returns at 14:00 today, one recipient at a time.
     * A screen that said "resets at midnight" would be wrong twice - too
     * pessimistic all morning and too optimistic all evening.
     */
    private OffsetDateTime nextFreeAt() {
        try {
            return jdbc.queryForObject("""
                    select min(first_seen) + interval '24 hours'
                      from (
                            select phone, min(created_at) as first_seen
                              from wa_message_log
                             where status = 'SENT'
                               and created_at > now() - interval '24 hours'
                               and phone is not null
                             group by phone
                           ) t
                    """, OffsetDateTime.class);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private long queuedCount() {
        Long count = jdbc.queryForObject(
                "select count(*) from wa_send_queue where state in ('PENDING', 'SENDING')",
                Long.class);
        return count == null ? 0L : count;
    }

    private Stored stored() {
        return jdbc.queryForObject("""
                select tier, tier_label, safety_margin, quality_rating, number_status, refreshed_at
                  from wa_quota where id = true
                """, (rs, n) -> new Stored(
                        rs.getInt("tier"),
                        rs.getString("tier_label"),
                        rs.getInt("safety_margin"),
                        rs.getString("quality_rating"),
                        rs.getString("number_status"),
                        rs.getObject("refreshed_at", OffsetDateTime.class)));
    }

    // ── deciding ────────────────────────────────────────────────────────────

    /**
     * How many of {@code phones} may be sent to right now.
     *
     * <p>Not simply {@code min(remaining, phones.size())}. Meta counts unique
     * <em>people</em>, so a recipient already messaged in the last 24 hours costs
     * nothing to message again - and in a school that is most of them. A lesson
     * of 100 parents who were all told about attendance this morning consumes
     * <b>zero</b> allowance for the absence message this afternoon, and a naive
     * {@code min()} would refuse to send it.
     *
     * @return the phones that may go now, in the order given, longest-waiting
     *         first once the caller has ordered them so
     */
    public List<String> admit(List<String> phones) {
        if (phones == null || phones.isEmpty()) {
            return List.of();
        }
        Quota quota = current();
        java.util.Set<String> alreadyCounted = countedPhones(phones);
        java.util.List<String> admitted = new java.util.ArrayList<>();
        long budget = quota.remaining();
        java.util.Set<String> newThisRun = new java.util.HashSet<>();
        for (String phone : phones) {
            boolean free = phone == null || alreadyCounted.contains(phone)
                    || newThisRun.contains(phone);
            if (free) {
                admitted.add(phone);
                continue;
            }
            if (budget <= 0) {
                break;
            }
            budget--;
            newThisRun.add(phone);
            admitted.add(phone);
        }
        return admitted;
    }

    /** Which of these numbers are already inside the rolling window, and so free. */
    public java.util.Set<String> countedPhones(List<String> phones) {
        if (phones == null || phones.isEmpty()) {
            return java.util.Set.of();
        }
        List<String> distinct = phones.stream().filter(java.util.Objects::nonNull).distinct()
                .toList();
        if (distinct.isEmpty()) {
            return java.util.Set.of();
        }
        List<String> found = jdbc.queryForList("""
                select distinct phone
                  from wa_message_log
                 where status = 'SENT'
                   and created_at > now() - interval '24 hours'
                   and phone = any (?)
                """, String.class, (Object) distinct.toArray(String[]::new));
        return new java.util.HashSet<>(found);
    }

    // ── refreshing from Meta ────────────────────────────────────────────────

    /**
     * Re-read the ceiling from Meta.
     *
     * <p>Every six hours, because that is how often Meta re-evaluates a tier
     * (it was 24 hours before October 2025). More often would spend the Graph
     * request budget for nothing; less often and a teacher would sit on a
     * ceiling they had already outgrown.
     */
    @Scheduled(fixedDelayString = "${app.meta.quota-refresh-ms:21600000}", initialDelay = 60_000L)
    @Transactional
    public void refresh() {
        if (!properties.meta().configured()) {
            return;
        }
        WhatsappInstance number = instances.findAllByOrderByCreatedAtAsc().stream()
                .filter(w -> w.getPhoneNumberId() != null && !w.getPhoneNumberId().isBlank())
                .findFirst()
                .orElse(null);
        if (number == null) {
            return;
        }
        try {
            CloudApiClient.NumberState state = cloud.fetchNumberState(number.getPhoneNumberId());
            apply(state);
            log.info("WhatsApp quota refreshed: tier={} quality={} status={}",
                    state.tierLabel(), state.qualityRating(), state.status());
        } catch (RuntimeException ex) {
            // A failed read must never lower the ceiling. The stored tier stays,
            // the error is recorded, and `stale` starts to tell the truth about
            // how old the number is.
            log.warn("Could not refresh the WhatsApp quota: {}", ex.getMessage());
            jdbc.update("update wa_quota set last_error = ?, updated_at = now() where id = true",
                    ex.getMessage());
        }
    }

    /**
     * Store what Meta reported.
     *
     * <p>A null tier leaves the stored one alone rather than defaulting it: a
     * label this code does not recognise - a tier Meta introduced after this was
     * written - must not silently demote a working account to 250.
     */
    @Transactional
    public void apply(CloudApiClient.NumberState state) {
        jdbc.update("""
                update wa_quota
                   set tier           = coalesce(?, tier),
                       tier_label     = coalesce(?, tier_label),
                       quality_rating = coalesce(?, quality_rating),
                       number_status  = coalesce(?, number_status),
                       throughput_level = coalesce(?, throughput_level),
                       refreshed_at   = now(),
                       last_error     = null,
                       updated_at     = now()
                 where id = true
                """, state.tier(), state.tierLabel(), state.qualityRating(), state.status(),
                state.throughputLevel());
    }

    /**
     * Apply a tier change Meta pushed on the {@code phone_number_quality_update}
     * webhook, so the gauge moves the moment the account is upgraded rather than
     * at the next scheduled read.
     */
    @Transactional
    public void applyWebhookTier(String eventOrTier, Integer currentLimit) {
        Integer tier = currentLimit;
        String label = eventOrTier;
        if (tier == null && eventOrTier != null && eventOrTier.toUpperCase().startsWith("TIER_")) {
            tier = switch (eventOrTier.toUpperCase()) {
                case "TIER_50" -> 50;
                case "TIER_250" -> 250;
                case "TIER_1K" -> 1_000;
                case "TIER_2K" -> 2_000;
                case "TIER_10K" -> 10_000;
                case "TIER_100K" -> 100_000;
                case "TIER_UNLIMITED" -> Integer.MAX_VALUE;
                default -> null;
            };
        }
        if (tier == null) {
            return;
        }
        jdbc.update("""
                update wa_quota
                   set tier = ?, tier_label = coalesce(?, tier_label),
                       refreshed_at = now(), last_error = null, updated_at = now()
                 where id = true
                """, tier, label);
        log.info("WhatsApp messaging limit changed to {} ({})", tier, label);
    }

    /** For the dashboard: the whole picture in one map, snake_case for the wire. */
    public Map<String, Object> snapshot() {
        Quota q = current();
        return Map.of("quota", q, "paused_for_seconds", Duration.ZERO.toSeconds());
    }
}
