package com.center.whatsapp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One month of WhatsApp usage for one workspace: what was sent, from which
 * numbers, to how many people, and what those messages cost.
 *
 * <p>Written for someone who does not know what an API is. Every number here has
 * a plain question behind it - "كم رسالة خرجت؟", "من أي رقم؟", "كام رقم جديد
 * كلّمناه؟" - and one that a teacher does have to hold: WhatsApp meters every
 * message the centre starts, so volume is money.
 *
 * @param month          the month reported, as {@code YYYY-MM}
 * @param attempted      every send attempt, delivered or not
 * @param sent           the ones WhatsApp accepted
 * @param failed         {@code attempted - sent}, precomputed for the screens
 * @param recipients     distinct numbers that received at least one message
 * @param newContacts    of those, the ones never messaged before this month
 * @param estimatedCost  what the delivered messages are expected to cost, in USD
 * @param costByCategory the cost estimate broken down by billing category
 * @param numbers        per connected number, its own volume
 * @param daily          one entry per day of the month, zeros included
 * @param byType         per message type (attendance, absence, ...), its volume
 */
public record WhatsappUsageResponse(
        String month,
        long attempted,
        long sent,
        long failed,
        long recipients,
        long newContacts,
        BigDecimal estimatedCost,
        List<CategoryCost> costByCategory,
        List<NumberUsage> numbers,
        List<DayVolume> daily,
        List<TypeVolume> byType) {

    /**
     * What one WhatsApp billing category accounted for.
     *
     * @param rate the per-message price used, so the arithmetic is auditable
     *             rather than a figure the screen simply asserts
     */
    public record CategoryCost(String category, long sent, BigDecimal rate, BigDecimal cost) {
    }

    /**
     * One number's month.
     *
     * @param instanceId null for messages that failed before a number was picked
     * @param connected  whether it is working right now
     */
    public record NumberUsage(
            UUID instanceId,
            String label,
            String phone,
            boolean connected,
            String qualityRating,
            long attempted,
            long sent) {
    }

    /** One calendar day. Days with no traffic are present with zeros. */
    public record DayVolume(LocalDate day, long attempted, long sent) {
    }

    /** One message type's month, named the way the teacher's screens name it. */
    public record TypeVolume(String code, String label, long attempted, long sent) {
    }
}
