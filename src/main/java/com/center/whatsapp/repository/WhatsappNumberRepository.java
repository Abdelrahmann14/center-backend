package com.center.whatsapp.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.center.whatsapp.entity.WhatsappNumber;

/**
 * Reads are workspace-scoped by {@code @TenantId}; the one query that runs
 * outside a request (the retry job's workspace sweep) says so explicitly.
 */
public interface WhatsappNumberRepository extends JpaRepository<WhatsappNumber, UUID> {

    Optional<WhatsappNumber> findByPhone(String phone);

    List<WhatsappNumber> findByPhoneIn(List<String> phones);

    /**
     * Numbers still waiting on an answer and still worth retrying, oldest first.
     *
     * <p>The attempt guard belongs in the query, not in the loop: a page filled
     * with numbers that have already exhausted their retries would otherwise be
     * fetched and skipped on every pass, and the ones behind them would never be
     * reached at all.
     */
    List<WhatsappNumber> findByHasWhatsappIsNullAndAttemptsLessThanOrderByCreatedAtAsc(
            int maxAttempts, Limit limit);

    /**
     * Workspaces with work waiting: either a number still unanswered, or a
     * student phone this table has never heard of. Native so it can run on the
     * scheduler thread, where no tenant is bound - the job then re-enters each
     * workspace one at a time.
     */
    @Query(value = """
            SELECT DISTINCT admin_id FROM whatsapp_numbers
            WHERE has_whatsapp IS NULL AND attempts < :maxAttempts
            UNION
            SELECT DISTINCT s.admin_id FROM students s
            WHERE s.deleted_at IS NULL
              AND EXISTS (
                SELECT 1 FROM unnest(coalesce(s.student_phones, '{}'::text[])
                                     || coalesce(s.parent_phones, '{}'::text[])) AS p
                WHERE p <> ''
                  AND NOT EXISTS (SELECT 1 FROM whatsapp_numbers w
                                  WHERE w.admin_id = s.admin_id AND w.phone = p))
            """, nativeQuery = true)
    List<UUID> workspacesNeedingCheck(int maxAttempts);

    /**
     * Student phones in this workspace that have never been queued.
     *
     * <p>Native because the phones are a {@code text[]} and this is an anti-join
     * over their flattened contents. The tenant is passed explicitly: the caller
     * is the scheduler, which binds one only for the duration of the callback.
     */
    @Query(value = """
            SELECT DISTINCT p
            FROM students s,
                 LATERAL unnest(coalesce(s.student_phones, '{}'::text[])
                                || coalesce(s.parent_phones, '{}'::text[])) AS p
            WHERE s.admin_id = :adminId
              AND s.deleted_at IS NULL
              AND p <> ''
              AND NOT EXISTS (SELECT 1 FROM whatsapp_numbers w
                              WHERE w.admin_id = :adminId AND w.phone = p)
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findUnknownStudentPhones(UUID adminId, int limit);
}
