package com.center.sync.service;
import com.center.student.entity.Student;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
import com.center.student.dto.StudentRequest;
import com.center.student.service.StudentService;
import com.center.group.dto.GroupRequest;
import com.center.group.service.GroupService;
import com.center.center.dto.CenterRequest;
import com.center.center.service.CenterService;
import com.center.lecture.dto.LectureRequest;
import com.center.lecture.service.LectureService;
import com.center.registration.dto.CreateRegistrationRequest;
import com.center.finance.dto.AttendanceRequest;
import com.center.finance.dto.FinanceEntryRequest;
import com.center.finance.service.FinanceService;
import com.center.registration.service.RegistrationService;
import com.center.outbox.entity.ExternalEffect;
import com.center.outbox.service.ExternalEffectOutbox;
import com.center.common.exception.DuplicateResourceException;

import java.math.BigDecimal;
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
    private static final String CENTER = "center";
    private static final String LECTURE = "lecture";
    private static final String REGISTRATION = "registration";
    private static final String FINANCE_ENTRY = "finance_entry";
    /** One assistant marked present at one session - pull-only, written as a set. */
    private static final String LESSON_ATTENDANCE = "lesson_attendance";
    /** The workspace's assistants, so the attendance form has names offline. */
    private static final String ASSISTANT = "assistant";
    /** Not a row: the whole tick-list for one session, replacing what was there. */
    private static final String ASSISTANT_ATTENDANCE = "assistant_attendance";
    private static final String EXAM_ATTEMPT = "exam_attempt";
    /** Not a row: a WhatsApp batch the user asked for while the line was down. */
    private static final String WHATSAPP_SEND = "whatsapp_send";
    private static final int MAX_PAGE = 500;
    private static final int DEFAULT_PAGE = 200;

    private final JdbcTemplate jdbc;
    /** Used by the pull resolvers, which bind an id LIST rather than a single id. */
    private final NamedParameterJdbcTemplate named;
    private final StudentExamService studentExamService;
    private final SyncMutationTx mutationTx;
    private final StudentService studentService;
    private final GroupService groupService;
    private final CenterService centerService;
    private final LectureService lectureService;
    private final RegistrationService registrationService;
    private final FinanceService financeService;
    private final ExternalEffectOutbox outbox;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    // --- Push -------------------------------------------------------------

    /**
     * Deliberately NOT {@code @Transactional}: every mutation gets its own
     * transaction through {@link SyncMutationTx}, so a failure rolls back that
     * one row instead of the batch. A batch-wide transaction here would defeat
     * that, because the client resends the same batch forever and one poison row
     * would stall the outbox permanently.
     */
    @Override
    public SyncPushResponse push(SyncPushRequest request) {
        UUID tenant = requireTenant();
        List<SyncMutationResult> results = new ArrayList<>();
        if (request.mutations() == null) {
            return new SyncPushResponse(results);
        }

        boolean student = callerIsStudent();

        for (SyncMutation m : request.mutations()) {
            try {
                results.add(mutationTx.run(tenant, m,
                        (mut, firstDelivery) -> dispatch(mut, tenant, student, firstDelivery)));
            } catch (DataIntegrityViolationException ex) {
                // A constraint the client could not have checked offline (a
                // missing FK target, a unique key another device already took).
                // Its transaction is gone; report it and keep draining the batch.
                log.warn("sync: mutation {} violated a constraint: {}", m.mutationId(), ex.getMostSpecificCause().getMessage());
                results.add(SyncMutationResult.rejected(m.mutationId(), m.rowId(),
                        "تعارض في البيانات - تعذّر حفظ هذا التعديل"));
            } catch (RuntimeException ex) {
                // Anything that reached here is unexpected - not a validation
                // refusal, not a constraint. The full stack is logged, but on a
                // hosted box the operator cannot read those logs, and a mutation
                // rejected for an invisible reason is a write silently lost. So
                // the root cause travels back in the rejection message too: the
                // client shows it, and one reproduction says exactly what broke.
                log.error("sync: mutation {} failed", m.mutationId(), ex);
                results.add(SyncMutationResult.rejected(m.mutationId(), m.rowId(),
                        "تعذّر تطبيق التعديل (" + rootCause(ex) + ")"));
            }
        }
        return new SyncPushResponse(results);
    }

    /** Route one mutation to its writer. Runs inside that mutation's own transaction. */
    private SyncMutationResult dispatch(SyncMutation m, UUID tenant, boolean student, boolean firstDelivery) {
        if (EXAM_ATTEMPT.equals(m.entity())) {
            return applyExamAttempt(m, firstDelivery);
        }
        if (ATTENDANCE.equals(m.entity())) {
            // A student may only push their own exam attempts, never attendance.
            return student
                    ? SyncMutationResult.rejected(m.mutationId(), m.rowId(), "غير مسموح")
                    : applyAttendance(m, tenant, firstDelivery);
        }
        // A student account may only author its own exam attempts; everything
        // below is workspace data that belongs to the teacher.
        if (STUDENT.equals(m.entity())) {
            return student
                    ? SyncMutationResult.rejected(m.mutationId(), m.rowId(), "غير مسموح")
                    : applyStudent(m, tenant, firstDelivery);
        }
        if (GROUP.equals(m.entity())) {
            return student
                    ? SyncMutationResult.rejected(m.mutationId(), m.rowId(), "غير مسموح")
                    : applyGroup(m, tenant, firstDelivery);
        }
        if (CENTER.equals(m.entity())) {
            return student
                    ? SyncMutationResult.rejected(m.mutationId(), m.rowId(), "غير مسموح")
                    : applyCenter(m, tenant, firstDelivery);
        }
        if (LECTURE.equals(m.entity())) {
            return student
                    ? SyncMutationResult.rejected(m.mutationId(), m.rowId(), "غير مسموح")
                    : applyVia(m, firstDelivery, LectureRequest.class,
                            r -> lectureService.upsert(m.rowId(), r),
                            () -> lectureService.delete(m.rowId()),
                            () -> lectureRow(m.rowId(), tenant), "بيانات الحصة");
        }
        if (REGISTRATION.equals(m.entity())) {
            return student
                    ? SyncMutationResult.rejected(m.mutationId(), m.rowId(), "غير مسموح")
                    : applyRegistration(m, tenant, firstDelivery);
        }
        if (WHATSAPP_SEND.equals(m.entity())) {
            return student
                    ? SyncMutationResult.rejected(m.mutationId(), m.rowId(), "غير مسموح")
                    : applyWhatsappSend(m, tenant, firstDelivery);
        }
        if (FINANCE_ENTRY.equals(m.entity())) {
            return student
                    ? SyncMutationResult.rejected(m.mutationId(), m.rowId(), "غير مسموح")
                    : applyVia(m, firstDelivery, FinanceEntryRequest.class,
                            r -> financeService.upsertEntry(m.rowId(), r),
                            () -> financeService.deleteEntry(m.rowId()),
                            () -> financeEntryRow(m.rowId(), tenant), "بيانات البند");
        }
        if (ASSISTANT_ATTENDANCE.equals(m.entity())) {
            return student
                    ? SyncMutationResult.rejected(m.mutationId(), m.rowId(), "غير مسموح")
                    : applyAssistantAttendance(m, firstDelivery);
        }
        return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "نوع غير مدعوم للمزامنة");
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

    /**
     * Replay a student written offline. The payload is the same snake_case body
     * the REST endpoint takes, and it goes through the same service - so the
     * offline path cannot drift from the online one on validation, on duplicate
     * detection, or on firing the Google Contacts event.
     *
     * <p>The row id is the CLIENT's, which is why {@code upsert} exists: the row
     * the device already showed its user must be this row, not a second copy.
     * A delete is idempotent by design - a student already gone is a success,
     * because the outcome the client asked for is the outcome that holds.
     */
    private SyncMutationResult applyStudent(SyncMutation m, UUID tenant, boolean firstDelivery) {
        if ("delete".equals(m.op())) {
            try {
                studentService.delete(m.rowId());
            } catch (ResourceNotFoundException ignored) {
                // Already deleted: the requested end state, so not a failure.
            }
            return firstDelivery
                    ? SyncMutationResult.applied(m.mutationId(), m.rowId(), null, 0L)
                    : SyncMutationResult.duplicate(m.mutationId(), m.rowId(), null, 0L);
        }

        Map<String, Object> p = m.payload();
        if (p == null) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "بيانات الطالب ناقصة");
        }
        try {
            StudentRequest request = objectMapper.convertValue(p, StudentRequest.class);
            validate(request);
            studentService.upsert(m.rowId(), request);
            Map<String, Object> row = studentRow(m.rowId(), tenant);
            long version = row != null && row.get("version") instanceof Number n ? n.longValue() : 0L;
            return firstDelivery
                    ? SyncMutationResult.applied(m.mutationId(), m.rowId(), row, version)
                    : SyncMutationResult.duplicate(m.mutationId(), m.rowId(), row, version);
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "بيانات الطالب غير صحيحة");
        }
    }

    /** Replay a weekly group written offline. */
    private SyncMutationResult applyGroup(SyncMutation m, UUID tenant, boolean firstDelivery) {
        return applyVia(m, firstDelivery, GroupRequest.class,
                request -> groupService.upsert(m.rowId(), request),
                () -> groupService.delete(m.rowId(), transferTarget(m.payload())),
                () -> groupRow(m.rowId(), tenant),
                "بيانات المجموعة");
    }

    /**
     * The group a deleted group's students are transferred to, if the offline delete
     * carried one. Absent (an empty group, or an older client), it is null and the
     * delete succeeds only when the group has no students.
     */
    private static UUID transferTarget(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object v = payload.get("transfer_to_group_id");
        return v == null ? null : UUID.fromString(v.toString());
    }

    /**
     * Replay a center written offline. Its price list rides inside the payload -
     * the service saves center and prices as one unit, so they sync as one too.
     */
    private SyncMutationResult applyCenter(SyncMutation m, UUID tenant, boolean firstDelivery) {
        return applyVia(m, firstDelivery, CenterRequest.class,
                request -> centerService.upsert(m.rowId(), request),
                () -> centerService.delete(m.rowId()),
                () -> centerRow(m.rowId(), tenant),
                "بيانات السنتر");
    }

    /**
     * Replay a registration written offline.
     *
     * <p>Not routed through {@link #applyVia} because the payload carries more
     * than the create request does: sync sends whole rows, so homework flag and
     * exam score travel with it rather than through the two field-level PATCH
     * endpoints the online UI uses.
     */
    private SyncMutationResult applyRegistration(SyncMutation m, UUID tenant, boolean firstDelivery) {
        if ("delete".equals(m.op())) {
            try {
                registrationService.unregister(m.rowId());
            } catch (ResourceNotFoundException ignored) {
                // Already gone: the requested end state holds.
            }
            return firstDelivery
                    ? SyncMutationResult.applied(m.mutationId(), m.rowId(), null, 0L)
                    : SyncMutationResult.duplicate(m.mutationId(), m.rowId(), null, 0L);
        }
        Map<String, Object> p = m.payload();
        if (p == null) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "بيانات التسجيل ناقصة");
        }
        try {
            CreateRegistrationRequest request =
                    objectMapper.convertValue(p, CreateRegistrationRequest.class);
            validate(request);
            Object score = p.get("exam_score");
            BigDecimal examScore = score == null ? null : new BigDecimal(String.valueOf(score));
            registrationService.upsert(m.rowId(), request, examScore);
            Map<String, Object> row = registrationRow(m.rowId(), tenant);
            long version = row != null && row.get("version") instanceof Number n ? n.longValue() : 0L;
            return firstDelivery
                    ? SyncMutationResult.applied(m.mutationId(), m.rowId(), row, version)
                    : SyncMutationResult.duplicate(m.mutationId(), m.rowId(), row, version);
        } catch (BusinessRuleException | DuplicateResourceException | ResourceNotFoundException ex) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "بيانات التسجيل غير صحيحة");
        }
    }

    /**
     * Replay a WhatsApp batch the user asked for while the device was offline.
     *
     * <p>Unlike every other mutation this writes no row of its own - it hands the
     * request to the external-effect outbox, whose drainer performs the send. That
     * indirection is the point: the push that carries it has only proved the
     * BROWSER is back, and Green API may still be unreachable from here. The
     * outbox retries until it is not.
     *
     * <p>Double-sending is impossible on two counts: the sync ledger makes a
     * re-delivered mutation a no-op, and the send itself skips any student already
     * messaged for that lesson.
     */
    private SyncMutationResult applyWhatsappSend(SyncMutation m, UUID tenant, boolean firstDelivery) {
        if (!firstDelivery) {
            return SyncMutationResult.duplicate(m.mutationId(), m.rowId(), null, 0L);
        }
        Map<String, Object> p = m.payload();
        if (p == null || p.get("lecture_id") == null || p.get("group_id") == null) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "بيانات الإرسال ناقصة");
        }
        try {
            outbox.enqueue(tenant, ExternalEffect.WHATSAPP_LECTURE, m.rowId(),
                    objectMapper.writeValueAsString(p));
            return SyncMutationResult.applied(m.mutationId(), m.rowId(), null, 0L);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "بيانات الإرسال غير صحيحة");
        }
    }

    /**
     * Replay the assistant tick-list for one session, set offline.
     *
     * <p>Not a row like the others: the form edits a SET, and the service replaces
     * it wholesale, so the mutation carries the whole list and there is no single
     * row id to answer with. That also makes it naturally idempotent - replaying
     * the same list leaves the same set - and makes a re-delivery a duplicate with
     * nothing to undo.
     *
     * <p>The rows it writes reach other devices through the change feed, not
     * through this answer: {@code lesson_attendance} is pull-only.
     */
    private SyncMutationResult applyAssistantAttendance(SyncMutation m, boolean firstDelivery) {
        if ("delete".equals(m.op())) {
            // The empty list is how a session is cleared; a delete op has no meaning.
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "نوع غير مدعوم للمزامنة");
        }
        if (m.payload() == null) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "بيانات الحضور ناقصة");
        }
        try {
            AttendanceRequest request = objectMapper.convertValue(m.payload(), AttendanceRequest.class);
            validate(request);
            if (firstDelivery) {
                financeService.setAttendance(request);
            }
            return firstDelivery
                    ? SyncMutationResult.applied(m.mutationId(), m.rowId(), null, 0L)
                    : SyncMutationResult.duplicate(m.mutationId(), m.rowId(), null, 0L);
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), "بيانات الحضور غير صحيحة");
        }
    }

    /**
     * The shape every entity's replay shares: decode the payload, validate it as
     * the REST layer would, hand it to the real service, and answer with the
     * stored row.
     *
     * <p>A delete is idempotent by design - a row already gone is the outcome
     * the client asked for, so it is a success, not a failure to report.
     */
    private <T> SyncMutationResult applyVia(SyncMutation m, boolean firstDelivery, Class<T> type,
            java.util.function.Consumer<T> write, Runnable remove,
            java.util.function.Supplier<Map<String, Object>> read, String what) {
        if ("delete".equals(m.op())) {
            try {
                remove.run();
            } catch (ResourceNotFoundException ignored) {
                // Already gone: the requested end state holds.
            }
            return firstDelivery
                    ? SyncMutationResult.applied(m.mutationId(), m.rowId(), null, 0L)
                    : SyncMutationResult.duplicate(m.mutationId(), m.rowId(), null, 0L);
        }
        if (m.payload() == null) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), what + " ناقصة");
        }
        try {
            T request = objectMapper.convertValue(m.payload(), type);
            validate(request);
            write.accept(request);
            Map<String, Object> row = read.get();
            long version = row != null && row.get("version") instanceof Number n ? n.longValue() : 0L;
            return firstDelivery
                    ? SyncMutationResult.applied(m.mutationId(), m.rowId(), row, version)
                    : SyncMutationResult.duplicate(m.mutationId(), m.rowId(), row, version);
        } catch (BusinessRuleException | DuplicateResourceException | ResourceNotFoundException ex) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return SyncMutationResult.rejected(m.mutationId(), m.rowId(), what + " غير صحيحة");
        }
    }

    /**
     * Bean validation does not run here - the payload arrived inside a sync
     * envelope, not as a {@code @Valid} request body - so it is applied by hand.
     * Without this an offline client could write rows the REST endpoints would
     * have refused.
     */
    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new BusinessRuleException(violations.iterator().next().getMessage());
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
                "SELECT seq, entity, row_id, op FROM sync_change_log "
                        + "WHERE admin_id = ? AND seq > ? ORDER BY seq ASC LIMIT ?",
                tenant, cursor, cap + 1);

        boolean hasMore = feed.size() > cap;
        if (hasMore) {
            feed = feed.subList(0, cap);
        }
        if (feed.isEmpty()) {
            return new SyncPullResponse(List.of(), String.valueOf(cursor), false);
        }
        cursor = ((Number) feed.get(feed.size() - 1).get("seq")).longValue();

        // Resolve every row this page needs, one statement per entity type.
        //
        // This is the point of the rewrite. Each feed entry used to be resolved
        // by its own query, so a page of 200 changes was 201 sequential round
        // trips - and a first sync of a large workspace, which walks the entire
        // feed, was thousands of them, every one of them holding this read-only
        // transaction's pooled connection open a little longer. It is now a
        // fixed handful of queries per page, however many rows the page carries.
        //
        // Deliberately NOT collapsed to one entry per row: the emitted list is
        // change-for-change identical to before, so ordering between entities
        // inside a page is untouched. A duplicate simply reuses the row already
        // fetched instead of fetching it again.
        Map<String, Set<UUID>> wanted = new LinkedHashMap<>();
        for (Map<String, Object> c : feed) {
            if (!"delete".equals(c.get("op"))) {
                wanted.computeIfAbsent((String) c.get("entity"), k -> new LinkedHashSet<>())
                        .add(uuid(c.get("row_id")));
            }
        }
        Map<String, Map<UUID, Map<String, Object>>> resolved = new LinkedHashMap<>();
        for (var e : wanted.entrySet()) {
            resolved.put(e.getKey(), rowsFor(e.getKey(), e.getValue(), tenant));
        }

        List<SyncEntityChange> changes = new ArrayList<>(feed.size());
        for (Map<String, Object> c : feed) {
            String entity = (String) c.get("entity");
            UUID rowId = uuid(c.get("row_id"));

            // The feed says so explicitly since V53. Before that a deletion was
            // only ever inferred from a row that failed to resolve, which never
            // fired for a hard delete because nothing was logged in the first
            // place.
            if ("delete".equals(c.get("op"))) {
                changes.add(new SyncEntityChange(entity, "delete", rowId, 0L, null));
                continue;
            }
            Map<UUID, Map<String, Object>> byId = resolved.get(entity);
            if (byId == null) {
                // A feed entry whose resolver is missing is a server bug, not
                // data: shipping it under the wrong shape is worse than skipping.
                log.error("sync: no row resolver for entity '{}' (row {})", entity, rowId);
                continue;
            }
            Map<String, Object> row = byId.get(rowId);
            if (row == null) {
                changes.add(new SyncEntityChange(entity, "delete", rowId, 0L, null));
            } else {
                long version = row.get("version") instanceof Number n ? n.longValue() : 0L;
                changes.add(new SyncEntityChange(entity, "upsert", rowId, version, row));
            }
        }
        return new SyncPullResponse(changes, String.valueOf(cursor), hasMore);
    }

    /**
     * Every row of one entity, keyed by id. Returns {@code null} for an entity
     * this server has no resolver for, which the caller reports as a bug rather
     * than guessing a shape.
     */
    private Map<UUID, Map<String, Object>> rowsFor(String entity, Set<UUID> ids, UUID tenant) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return switch (entity) {
            case STUDENT -> studentRows(ids, tenant);
            case GROUP -> groupRows(ids, tenant);
            case CENTER -> centerRows(ids, tenant);
            case LECTURE -> lectureRows(ids, tenant);
            case REGISTRATION -> registrationRows(ids, tenant);
            case ATTENDANCE -> attendanceRows(ids, tenant);
            case FINANCE_ENTRY -> financeEntryRows(ids, tenant);
            case LESSON_ATTENDANCE -> lessonAttendanceRows(ids, tenant);
            case ASSISTANT -> assistantRows(ids, tenant);
            default -> null;
        };
    }

    // --- Row resolvers (timestamps cast to text so the JSON stays clean) ----

    /**
     * The full student row, not a roster subset.
     *
     * <p>It started narrow, for an attendance screen that only needed a name and
     * a group. Now the mirror has to be able to stand in for the students page
     * itself, and a narrow row would be worse than no row: applying it
     * OVERWRITES the local copy, so every sync would quietly strip the phones,
     * school and price off a student the client already had in full.
     *
     * <p>The field names have to match {@code StudentResponse} exactly, because
     * the pages render this row directly. {@code is_active} is emitted under BOTH
     * names: the web and mobile roster screens have read {@code active} since the
     * feed existed, while every page reads {@code is_active} - sending only the
     * alias made every student render as blocked.
     *
     * <p>{@code registered} and {@code google_synced} are derived by the REST
     * mapper rather than stored, so they are computed here too; without them the
     * table shows every student as having no app account and no Google contact.
     */
    private Map<UUID, Map<String, Object>> studentRows(Set<UUID> ids, UUID tenant) {
        return byId(named.queryForList(
                "SELECT s.id, s.serial, s.name, s.grade, s.school, s.city, s.gender, s.group_id, "
                        + "s.student_phones, s.parent_phones, s.religion, s.academic_track, "
                        + "s.lesson_price, s.is_discounted, s.notes, s.block_reason, "
                        + "s.is_active, s.is_active AS active, "
                        + "(s.user_id IS NOT NULL) AS registered, "
                        + "EXISTS (SELECT 1 FROM google_contact_link g "
                        + "        WHERE g.admin_id = s.admin_id AND g.subject_id = s.id) AS google_synced, "
                        + "s.deleted_at::text AS deleted_at, s.version, "
                        + "s.created_at::text AS created_at, s.created_by, "
                        + "s.updated_at::text AS updated_at, s.updated_by "
                        + "FROM students s WHERE s.id IN (:ids) AND s.admin_id = :tenant",
                scope(ids, tenant)), SyncServiceImpl::normalisePhoneArrays);
    }

    private Map<String, Object> studentRow(UUID id, UUID tenant) {
        return studentRows(Set.of(id), tenant).get(id);
    }

    /**
     * Postgres text[] arrives as a {@code java.sql.Array}, which Jackson cannot
     * serialise. Unwrap both phone columns to plain lists.
     */
    private static Map<String, Object> normalisePhoneArrays(Map<String, Object> row) {
        for (String key : new String[] {"student_phones", "parent_phones"}) {
            if (row.get(key) instanceof java.sql.Array array) {
                try {
                    Object[] values = (Object[]) array.getArray();
                    row.put(key, List.of(values).stream().map(String::valueOf).toList());
                } catch (java.sql.SQLException ex) {
                    row.put(key, List.of());
                }
            }
        }
        return row;
    }

    /**
     * A group as the pages render it.
     *
     * <p>{@code is_active} goes out under BOTH names on purpose: the web reads
     * {@code is_active} (the REST contract) while the mobile attendance screen
     * reads the older {@code active} alias, and a row carrying only one of them
     * leaves the other side reading {@code undefined} - which is falsy, so every
     * group would render as disabled.
     *
     * <p>{@code student_count} is a {@code @Formula} the clients recompute from
     * their own mirror - a head count that will not move when a student is added
     * offline reads as broken, not as stale - so it is left out. The other two
     * derived columns cannot be recomputed and are sent: {@code last_attendance}
     * is written by lesson registration rather than by this page, and
     * {@code lesson_price} is the center's rate for this grade, which the student
     * form uses as the default price and the ceiling for a discount.
     */
    private Map<UUID, Map<String, Object>> groupRows(Set<UUID> ids, UUID tenant) {
        return byId(named.queryForList(
                "SELECT g.id, g.grade, g.day_of_week, g.start_time::text AS start_time, "
                        + "g.center_name, g.is_active, g.is_active AS active, g.version, "
                        + "(SELECT max(a.attended_on)::text FROM attendance a "
                        + " WHERE a.group_id = g.id) AS last_attendance, "
                        + "(SELECT cg.price FROM center_grades cg "
                        + " JOIN centers c ON c.id = cg.center_id "
                        + " WHERE c.name = g.center_name AND cg.grade = g.grade "
                        + "   AND c.admin_id = g.admin_id) AS lesson_price, "
                        // The center's share of this group's takings. Sent because a
                        // disconnected Financials screen derives its own invoices, and
                        // without the share it would show the gross as the teacher's.
                        + "(SELECT cg.percentage FROM center_grades cg "
                        + " JOIN centers c ON c.id = cg.center_id "
                        + " WHERE c.name = g.center_name AND cg.grade = g.grade "
                        + "   AND c.admin_id = g.admin_id) AS center_percentage, "
                        + "g.updated_at::text AS updated_at "
                        + "FROM groups g WHERE g.id IN (:ids) AND g.admin_id = :tenant",
                scope(ids, tenant)), row -> row);
    }

    private Map<String, Object> groupRow(UUID id, UUID tenant) {
        return groupRows(Set.of(id), tenant).get(id);
    }

    /**
     * A center with its price list embedded, matching what the REST endpoint
     * returns - the clients read prices straight off the center, and a center
     * without them would render an empty price card.
     */
    private Map<UUID, Map<String, Object>> centerRows(Set<UUID> ids, UUID tenant) {
        Map<String, Object> params = scope(ids, tenant);
        Map<UUID, Map<String, Object>> centers = byId(named.queryForList(
                "SELECT id, name, is_active, is_active AS active, version, "
                        + "created_at::text AS created_at, updated_at::text AS updated_at "
                        + "FROM centers WHERE id IN (:ids) AND admin_id = :tenant",
                params), row -> row);

        // The price list for every center at once, then bucketed - the shape the
        // clients read is unchanged, so center_id is dropped once it has been
        // used to bucket.
        for (Map<String, Object> row : centers.values()) {
            row.put("grades", new ArrayList<Map<String, Object>>());
        }
        for (Map<String, Object> price : named.queryForList(
                "SELECT center_id, grade, price FROM center_grades "
                        + "WHERE center_id IN (:ids) AND admin_id = :tenant ORDER BY center_id, grade",
                params)) {
            Map<String, Object> center = centers.get(uuid(price.remove("center_id")));
            if (center != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> grades = (List<Map<String, Object>>) center.get("grades");
                grades.add(price);
            }
        }
        return centers;
    }

    private Map<String, Object> centerRow(UUID id, UUID tenant) {
        return centerRows(Set.of(id), tenant).get(id);
    }

    private Map<UUID, Map<String, Object>> lectureRows(Set<UUID> ids, UUID tenant) {
        return byId(named.queryForList(
                "SELECT id, name, grade, exam_name, exam_grade, has_exam, homework, version, "
                        + "created_at::text AS created_at, created_by, "
                        + "updated_at::text AS updated_at, updated_by "
                        + "FROM lectures WHERE id IN (:ids) AND admin_id = :tenant",
                scope(ids, tenant)), row -> row);
    }

    private Map<String, Object> lectureRow(UUID id, UUID tenant) {
        return lectureRows(Set.of(id), tenant).get(id);
    }

    /**
     * A registration with the student fields the desk screens render flattened
     * onto it, matching {@code RegistrationResponse} - the pages read the name
     * and code straight off the row rather than joining client-side.
     */
    private Map<UUID, Map<String, Object>> registrationRows(Set<UUID> ids, UUID tenant) {
        return byId(named.queryForList(
                "SELECT r.id, r.lecture_id, r.student_id, r.group_id, r.status, "
                        + "r.exam_score, r.homework_flag, r.registered_by, r.version, "
                        + "r.created_at::text AS created_at, r.attended_at::text AS attended_at, "
                        + "r.updated_at::text AS updated_at, "
                        + "s.name AS student_name, s.serial AS student_serial, "
                        + "s.grade AS student_grade, s.group_id AS assigned_group_id "
                        + "FROM registrations r JOIN students s ON s.id = r.student_id "
                        + "WHERE r.id IN (:ids) AND r.admin_id = :tenant",
                scope(ids, tenant)), row -> row);
    }

    private Map<String, Object> registrationRow(UUID id, UUID tenant) {
        return registrationRows(Set.of(id), tenant).get(id);
    }

    /** A manual invoice line, shaped exactly like {@code FinanceEntryResponse}. */
    private Map<UUID, Map<String, Object>> financeEntryRows(Set<UUID> ids, UUID tenant) {
        return byId(named.queryForList(
                "SELECT id, lecture_id, group_id, session_date::text AS session_date, "
                        + "kind, description, amount, version, "
                        + "created_at::text AS created_at, created_by, "
                        + "updated_at::text AS updated_at, updated_by "
                        + "FROM finance_entries WHERE id IN (:ids) AND admin_id = :tenant",
                scope(ids, tenant)), row -> row);
    }

    private Map<String, Object> financeEntryRow(UUID id, UUID tenant) {
        return financeEntryRows(Set.of(id), tenant).get(id);
    }

    /**
     * One assistant's presence at one session. The invoice reads these by session
     * and joins the names itself, so only the ids travel.
     */
    private Map<UUID, Map<String, Object>> lessonAttendanceRows(Set<UUID> ids, UUID tenant) {
        return byId(named.queryForList(
                "SELECT id, lecture_id, group_id, session_date::text AS session_date, "
                        + "user_id, version, created_at::text AS created_at "
                        + "FROM lesson_attendances WHERE id IN (:ids) AND admin_id = :tenant",
                scope(ids, tenant)), row -> row);
    }

    /**
     * An assistant, as the attendance form lists them: a name and whether the
     * account is still live. The photo is left out on purpose - it is stored
     * in-row as bytes, the form draws no avatar, and a feed that carried one per
     * assistant would be paying for a picture nobody looks at.
     */
    private Map<UUID, Map<String, Object>> assistantRows(Set<UUID> ids, UUID tenant) {
        return byId(named.queryForList(
                "SELECT id, username, role, is_active, is_active AS active, version "
                        + "FROM users WHERE id IN (:ids) AND admin_id = :tenant AND role = 'user'",
                scope(ids, tenant)), row -> row);
    }

    private Map<UUID, Map<String, Object>> attendanceRows(Set<UUID> ids, UUID tenant) {
        return byId(named.queryForList(
                "SELECT id, group_id, student_id, attended_on::text AS attended_on, "
                        + "created_at::text AS created_at "
                        + "FROM attendance WHERE id IN (:ids) AND admin_id = :tenant",
                scope(ids, tenant)), row -> row);
    }

    private Map<String, Object> attendanceRow(UUID id, UUID tenant) {
        return attendanceRows(Set.of(id), tenant).get(id);
    }

    /** Bind parameters shared by every batch resolver. */
    private static Map<String, Object> scope(Set<UUID> ids, UUID tenant) {
        return Map.of("ids", ids, "tenant", tenant);
    }

    /** Index rows by their {@code id} column, applying {@code shape} to each. */
    private static Map<UUID, Map<String, Object>> byId(List<Map<String, Object>> rows,
            java.util.function.UnaryOperator<Map<String, Object>> shape) {
        Map<UUID, Map<String, Object>> out = new LinkedHashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            out.put(uuid(row.get("id")), shape.apply(row));
        }
        return out;
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

    /**
     * The deepest cause's type and message, for an operator-visible rejection.
     * A JDBC failure wraps the real Postgres error several layers down, so the
     * useful line ("relation ... does not exist", "null value in column ...") is
     * only reached by unwinding to the bottom.
     */
    private static String rootCause(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        String label = root.getClass().getSimpleName();
        if (message == null || message.isBlank()) {
            return label;
        }
        message = message.strip();
        // Keep it to one readable line in a toast.
        if (message.length() > 300) {
            message = message.substring(0, 300) + "…";
        }
        return label + ": " + message;
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
