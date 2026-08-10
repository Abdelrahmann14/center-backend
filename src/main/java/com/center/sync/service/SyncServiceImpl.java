package com.center.sync.service;
import com.center.student.entity.Student;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.exam.dto.StudentAnswerInput;
import com.center.exam.dto.StudentExamSubmitRequest;
import com.center.sync.dto.SyncEntityChange;
import com.center.sync.dto.SyncMutation;
import com.center.sync.dto.SyncMutationResult;
import com.center.sync.dto.SyncPullResponse;
import com.center.sync.dto.SyncPushRequest;
import com.center.sync.dto.SyncPushResponse;
import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.auth.security.AuthenticatedUser;
import com.center.exam.service.StudentExamService;
import com.center.sync.service.SyncService;
import com.center.common.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JDBC-backed sync service. It deliberately bypasses Hibernate's {@code @TenantId}
 * path and filters {@code admin_id} explicitly against the tenant bound by the
 * JWT filter, because the change feed and the idempotency ledger are plain tables
 * outside the entity model.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncServiceImpl implements SyncService {

    /** Entities a client may push. Students/groups remain pull-only. */
    private static final String ATTENDANCE = "attendance";
    private static final String STUDENT = "student";
    private static final String GROUP = "group";
    private static final String EXAM_ATTEMPT = "exam_attempt";
    private static final int MAX_PAGE = 500;
    private static final int DEFAULT_PAGE = 200;

    private final JdbcTemplate jdbc;
    private final StudentExamService studentExamService;

    // --- Push -------------------------------------------------------------

    @Override
    @Transactional
    public SyncPushResponse push(SyncPushRequest request) {
        UUID tenant = requireTenant();
        List<SyncMutationResult> results = new ArrayList<>();
        if (request.mutations() == null) {
            return new SyncPushResponse(results);
        }

        boolean student = callerIsStudent();

        for (SyncMutation m : request.mutations()) {
            // Idempotency: a mutation id is recorded once per tenant. A second
            // delivery inserts zero rows and is reported as a duplicate.
            int claimed = jdbc.update(
                    "INSERT INTO sync_applied_mutations (admin_id, mutation_id) "
                            + "VALUES (?, ?) ON CONFLICT DO NOTHING",
                    tenant, m.mutationId());
            boolean firstDelivery = claimed == 1;

            if (EXAM_ATTEMPT.equals(m.entity())) {
                results.add(applyExamAttempt(m, firstDelivery));
            } else if (ATTENDANCE.equals(m.entity())) {
                // A student may only push their own exam attempts, never attendance.
                if (student) {
                    results.add(SyncMutationResult.rejected(m.mutationId(), m.rowId(), "غير مسموح"));
                } else {
                    results.add(applyAttendance(m, tenant, firstDelivery));
                }
            } else if (STUDENT.equals(m.entity())) {
                // Student offline writes need full server-side validation; they
                // arrive in a later phase. Pull (roster read) already works.
                results.add(SyncMutationResult.rejected(m.mutationId(), m.rowId(),
                        "تعديل الطلاب دون اتصال غير مدعوم بعد"));
            } else {
                results.add(SyncMutationResult.rejected(m.mutationId(), m.rowId(),
                        "نوع غير مدعوم للمزامنة"));
            }
        }
        return new SyncPushResponse(results);
    }

    /**
     * Replay an offline exam submission. The idempotency ledger makes a re-delivery
     * a no-op duplicate; on first delivery the submission is graded authoritatively
     * (the client score is never trusted) in its OWN transaction, so a rejection
     * cannot poison the rest of the push batch. Ownership is the authenticated
     * student - any student_id in the payload is ignored.
     */
    private SyncMutationResult applyExamAttempt(SyncMutation m, boolean firstDelivery) {
        if (!firstDelivery) {
            return SyncMutationResult.duplicate(m.mutationId(), m.rowId(), null, 0L);
        }
        Map<String, Object> p = m.payload();
        if (p == null) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "بيانات المحاولة ناقصة");
        }
        try {
            UUID examId = uuid(p.get("exam_id"));
            OffsetDateTime startedAt = p.get("started_at") == null
                    ? null : OffsetDateTime.parse(String.valueOf(p.get("started_at")));
            StudentExamSubmitRequest req = new StudentExamSubmitRequest(startedAt, parseAnswers(p.get("answers")));
            studentExamService.submitOffline(examId, currentUserId(), req);
            return SyncMutationResult.applied(m.mutationId(), m.rowId(), null, 0L);
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), ex.getMessage());
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "بيانات المحاولة غير صحيحة");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<StudentAnswerInput> parseAnswers(Object raw) {
        List<StudentAnswerInput> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> mp) {
                    UUID questionId = uuid(mp.get("question_id"));
                    List<UUID> choiceIds = new ArrayList<>();
                    if (mp.get("choice_ids") instanceof List<?> cl) {
                        for (Object c : cl) {
                            choiceIds.add(uuid(c));
                        }
                    }
                    out.add(new StudentAnswerInput(questionId, choiceIds));
                }
            }
        }
        return out;
    }

    private static AuthenticatedUser principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return u;
        }
        throw new BusinessRuleException("لا يوجد مستخدم للمزامنة");
    }

    private static UUID currentUserId() {
        return principal().getId();
    }

    private static boolean callerIsStudent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedUser u && u.getRole() == Role.STUDENT;
    }

    /**
     * Append-only attendance: insert the mark, collapsing any duplicate (same
     * student/group/day, or a replayed row id) onto the existing row. Always
     * idempotent, so it never conflicts.
     */
    private SyncMutationResult applyAttendance(SyncMutation m, UUID tenant, boolean firstDelivery) {
        Map<String, Object> p = m.payload();
        if (p == null) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "بيانات الحضور ناقصة");
        }
        try {
            UUID groupId = uuid(p.get("group_id"));
            UUID studentId = uuid(p.get("student_id"));
            LocalDate on = LocalDate.parse(String.valueOf(p.get("attended_on")));

            jdbc.update(
                    "INSERT INTO attendance (id, admin_id, group_id, student_id, attended_on) "
                            + "VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    m.rowId(), tenant, groupId, studentId, on);

            Map<String, Object> row = attendanceRow(m.rowId(), tenant);
            if (row == null) {
                // A duplicate on the daily unique index resolves to the row that
                // already owns that day, not the replayed id.
                row = attendanceRow(groupId, studentId, on, tenant);
            }
            return firstDelivery
                    ? SyncMutationResult.applied(m.mutationId(), m.rowId(), row, 0L)
                    : SyncMutationResult.duplicate(m.mutationId(), m.rowId(), row, 0L);
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException ex) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "بيانات الحضور غير صحيحة");
        }
    }

    // --- Pull -------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SyncPullResponse pull(String since, int limit) {
        UUID tenant = TenantContext.get();
        long cursor = parseCursor(since);
        if (tenant == null) {
            // A root account (super admin) owns no workspace data to sync.
            return new SyncPullResponse(List.of(), String.valueOf(cursor), false);
        }
        int cap = limit > 0 && limit <= MAX_PAGE ? limit : DEFAULT_PAGE;

        // One extra row tells us whether more pages remain.
        List<Map<String, Object>> feed = jdbc.queryForList(
                "SELECT seq, entity, row_id FROM sync_change_log "
                        + "WHERE admin_id = ? AND seq > ? ORDER BY seq ASC LIMIT ?",
                tenant, cursor, cap + 1);

        boolean hasMore = feed.size() > cap;
        if (hasMore) {
            feed = feed.subList(0, cap);
        }

        List<SyncEntityChange> changes = new ArrayList<>(feed.size());
        for (Map<String, Object> c : feed) {
            cursor = ((Number) c.get("seq")).longValue();
            String entity = (String) c.get("entity");
            UUID rowId = uuid(c.get("row_id"));
            Map<String, Object> row;
            if (STUDENT.equals(entity)) {
                row = studentRow(rowId, tenant);
            } else if (GROUP.equals(entity)) {
                row = groupRow(rowId, tenant);
            } else {
                row = attendanceRow(rowId, tenant);
            }
            if (row == null) {
                changes.add(new SyncEntityChange(entity, "delete", rowId, 0L, null));
            } else {
                long version = row.get("version") instanceof Number n ? n.longValue() : 0L;
                changes.add(new SyncEntityChange(entity, "upsert", rowId, version, row));
            }
        }
        return new SyncPullResponse(changes, String.valueOf(cursor), hasMore);
    }

    // --- Row resolvers (timestamps cast to text so the JSON stays clean) ----

    private Map<String, Object> studentRow(UUID id, UUID tenant) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, serial, name, grade, group_id, is_active AS active, "
                        + "deleted_at::text AS deleted_at, version, updated_at::text AS updated_at "
                        + "FROM students WHERE id = ? AND admin_id = ?",
                id, tenant);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> groupRow(UUID id, UUID tenant) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, grade, day_of_week, start_time::text AS start_time, center_name, "
                        + "is_active AS active, version, updated_at::text AS updated_at "
                        + "FROM groups WHERE id = ? AND admin_id = ?",
                id, tenant);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> attendanceRow(UUID id, UUID tenant) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, group_id, student_id, attended_on::text AS attended_on, "
                        + "created_at::text AS created_at "
                        + "FROM attendance WHERE id = ? AND admin_id = ?",
                id, tenant);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> attendanceRow(UUID groupId, UUID studentId, LocalDate on, UUID tenant) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, group_id, student_id, attended_on::text AS attended_on, "
                        + "created_at::text AS created_at "
                        + "FROM attendance WHERE admin_id = ? AND group_id = ? "
                        + "AND student_id = ? AND attended_on = ?",
                tenant, groupId, studentId, on);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // --- Helpers ----------------------------------------------------------

    private static UUID requireTenant() {
        UUID tenant = TenantContext.get();
        if (tenant == null) {
            throw new com.center.common.exception.BusinessRuleException("لا يوجد سياق صلاحية للمزامنة");
        }
        return tenant;
    }

    private static long parseCursor(String since) {
        if (since == null || since.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(since.trim()));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID u) {
            return u;
        }
        return UUID.fromString(String.valueOf(value));
    }
}
