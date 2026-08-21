package com.center.messaging.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.center.messaging.entity.WhatsappMessageLog;

public interface WhatsappMessageLogRepository extends JpaRepository<WhatsappMessageLog, UUID> {

    /** The workspace's send history, newest first (scoped by @TenantId). */
    Page<WhatsappMessageLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Students who already received a delivered message for this lesson + origin. */
    @Query("""
            SELECT DISTINCT m.studentId FROM WhatsappMessageLog m
            WHERE m.lectureId = :lectureId AND m.origin = :origin
              AND m.status = 'SENT' AND m.studentId IS NOT NULL
            """)
    Set<UUID> sentStudentIds(@Param("lectureId") UUID lectureId, @Param("origin") String origin);

    /** Whether one student already got a delivered message for this lesson + origin. */
    boolean existsByStudentIdAndLectureIdAndOriginAndStatus(
            UUID studentId, UUID lectureId, String origin, String status);

    /** Whether one student already got a delivered message for this origin (no lesson scope). */
    boolean existsByStudentIdAndOriginAndStatus(UUID studentId, String origin, String status);

    // ---- usage reporting ---------------------------------------------------
    //
    // Native and tenant-explicit on purpose. @TenantId filters entity queries,
    // not native ones, so every query below takes the workspace as a parameter -
    // getting that wrong here would report one teacher's volume to another.

    /** Totals for the window: attempted, delivered, and distinct people reached. */
    @Query(value = """
            SELECT count(*)                                     AS "attempted",
                   count(*) FILTER (WHERE status = 'SENT')      AS "sent",
                   count(DISTINCT phone) FILTER (WHERE status = 'SENT') AS "recipients"
            FROM wa_message_log
            WHERE admin_id = :adminId AND created_at >= :from AND created_at < :to
            """, nativeQuery = true)
    UsageTotals totals(@Param("adminId") UUID adminId,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /**
     * People messaged in the window who had never been messaged before it.
     *
     * <p>Meta's limit is on distinct customers per rolling day, not on messages,
     * so "how many new numbers did we open a conversation with" is the figure
     * that predicts hitting it - a thousand messages to the same fifty parents
     * is not close to the ceiling.
     */
    @Query(value = """
            SELECT count(*) FROM (
                SELECT DISTINCT l.phone
                FROM wa_message_log l
                WHERE l.admin_id = :adminId AND l.created_at >= :from AND l.created_at < :to
                  AND l.phone IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM wa_message_log p
                      WHERE p.admin_id = l.admin_id AND p.phone = l.phone
                        AND p.created_at < :from)
            ) fresh
            """, nativeQuery = true)
    long newContacts(@Param("adminId") UUID adminId,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** Volume per number, so each connected number can show its own usage. */
    @Query(value = """
            SELECT instance_id                                AS "instanceId",
                   count(*)                                   AS "attempted",
                   count(*) FILTER (WHERE status = 'SENT')    AS "sent"
            FROM wa_message_log
            WHERE admin_id = :adminId AND created_at >= :from AND created_at < :to
            GROUP BY instance_id
            """, nativeQuery = true)
    List<NumberUsage> perNumber(@Param("adminId") UUID adminId,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** One row per day that saw traffic; the gaps are filled in by the service. */
    @Query(value = """
            SELECT (created_at AT TIME ZONE 'Africa/Cairo')::date  AS "day",
                   count(*)                                        AS "attempted",
                   count(*) FILTER (WHERE status = 'SENT')         AS "sent"
            FROM wa_message_log
            WHERE admin_id = :adminId AND created_at >= :from AND created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DailyVolume> perDay(@Param("adminId") UUID adminId,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /**
     * Delivered template messages by billing category - the cost estimate's input.
     *
     * <p>Filtered on the category rather than on delivery alone, because only a
     * template message is billed. Rows from before every message became one carry
     * no category, and counting them would invent a charge that was never made.
     */
    @Query(value = """
            SELECT coalesce(template_category, 'UNKNOWN') AS "category",
                   count(*)                               AS "sent"
            FROM wa_message_log
            WHERE admin_id = :adminId AND created_at >= :from AND created_at < :to
              AND template_category IS NOT NULL AND status = 'SENT'
            GROUP BY 1
            """, nativeQuery = true)
    List<CategoryCount> perCategory(@Param("adminId") UUID adminId,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** Volume per message type, so a teacher sees which message dominates. */
    @Query(value = """
            SELECT origin                                  AS "origin",
                   count(*)                                AS "attempted",
                   count(*) FILTER (WHERE status = 'SENT') AS "sent"
            FROM wa_message_log
            WHERE admin_id = :adminId AND created_at >= :from AND created_at < :to
            GROUP BY origin
            """, nativeQuery = true)
    List<OriginCount> perOrigin(@Param("adminId") UUID adminId,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // ---- WhatsApp reachability ---------------------------------------------

    /**
     * The most recent INFORMATIVE outcome per number in one workspace.
     *
     * <p>The official API has no endpoint that answers "is this number on
     * WhatsApp" - the on-premises contacts endpoint that used to is gone with
     * the on-premises API itself - so the answer is derived from what actually
     * happened to the messages already sent. A delivery report proves a WhatsApp
     * client received it; error 131026 says Meta could not put it in front of
     * anyone, which for a number that is simply not on WhatsApp is the only
     * signal there is.
     *
     * <p>Rows that say NOTHING are excluded rather than ranked: a send still
     * waiting on its callback, or one rejected for a reason about us (no
     * template, bad media), tells you nothing about the recipient, and letting
     * one be "most recent" would erase a real answer underneath it. Newest wins
     * among the rest, so a number that starts working stops being reported as
     * unreachable the moment a message lands.
     */
    @Query(value = """
            SELECT DISTINCT ON (phone)
                   phone                                              AS "phone",
                   (delivered_at IS NOT NULL OR read_at IS NOT NULL)  AS "reached"
            FROM wa_message_log
            WHERE admin_id = :adminId
              AND phone IS NOT NULL
              AND (delivered_at IS NOT NULL OR read_at IS NOT NULL OR failure_code = 131026)
            ORDER BY phone, created_at DESC
            """, nativeQuery = true)
    List<PhoneReach> reachability(@Param("adminId") UUID adminId);

    /** One number and whether the last thing we learned about it was good. */
    interface PhoneReach {
        String getPhone();

        boolean getReached();
    }

    interface UsageTotals {
        long getAttempted();

        long getSent();

        long getRecipients();
    }

    interface NumberUsage {
        UUID getInstanceId();

        long getAttempted();

        long getSent();
    }

    interface DailyVolume {
        LocalDate getDay();

        long getAttempted();

        long getSent();
    }

    interface CategoryCount {
        String getCategory();

        long getSent();
    }

    interface OriginCount {
        String getOrigin();

        long getAttempted();

        long getSent();
    }
}
