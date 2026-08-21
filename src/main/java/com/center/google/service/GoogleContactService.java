package com.center.google.service;
import com.center.google.event.GoogleContactEvents;
import com.center.google.client.GoogleOAuthClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.google.dto.GoogleMarkRequest;
import com.center.google.dto.GoogleMarkResponse;
import com.center.google.client.GoogleRateLimitException;
import com.center.google.dto.GoogleResyncBatch;
import com.center.google.dto.GoogleResyncResult;
import com.center.google.dto.GoogleStatusResponse;
import com.center.google.entity.GoogleAccount;
import com.center.grade.entity.Grade;
import com.center.google.entity.GradeContactMark;
import com.center.student.entity.Student;
import com.center.common.exception.BusinessRuleException;
import com.center.google.repository.GoogleAccountRepository;
import com.center.google.repository.GradeContactMarkRepository;
import com.center.grade.repository.GradeRepository;
import com.center.student.repository.StudentRepository;
import com.center.common.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * Admin-facing Google Contacts management: connect / disconnect Google accounts
 * via OAuth, and configure the per-grade naming marks. The current admin is the
 * tenant bound on the request ({@link TenantContext}).
 */
@Service
@RequiredArgsConstructor
public class GoogleContactService {

    private final GoogleAccountRepository accountRepo;
    private final GradeContactMarkRepository markRepo;
    private final GradeRepository gradeRepo;
    private final StudentRepository studentRepository;
    private final GoogleOAuthClient oauth;
    private final GoogleContactSyncService syncService;
    private final org.springframework.context.ApplicationEventPublisher events;

    private UUID adminId() {
        UUID id = TenantContext.get();
        if (id == null) {
            throw new BusinessRuleException("هذه الصفحة متاحة لحسابات المدرّسين فقط");
        }
        return id;
    }

    /**
     * Google Contacts is available to every admin, unconditionally.
     *
     * <p>It used to sit behind a per-admin switch the super admin had to flip.
     * That switch is gone: syncing contacts costs the platform nothing, so
     * gating it only produced workspaces that silently failed to save numbers.
     * The only thing that can still block it is the server-side OAuth
     * configuration, which is a deployment fact rather than a per-admin one.
     */
    private static boolean enabled(UUID adminId) {
        return true;
    }

    @Transactional(readOnly = true)
    public GoogleStatusResponse status() {
        UUID adminId = adminId();
        List<GoogleStatusResponse.Account> accounts = accountRepo.findByAdminIdOrderByEmailAsc(adminId).stream()
                .map(a -> new GoogleStatusResponse.Account(a.getId(), a.getEmail()))
                .toList();
        return new GoogleStatusResponse(enabled(adminId), oauth.configured(), accounts);
    }

    /** The Google consent URL. Blocked when the feature is off or unconfigured. */
    @Transactional(readOnly = true)
    public String authUrl() {
        UUID adminId = adminId();
        if (!oauth.configured()) {
            throw new BusinessRuleException("لم يتم إعداد تكامل Google بعد");
        }
        return oauth.authUrl(UUID.randomUUID().toString());
    }

    /**
     * Exchange the OAuth code and store (or refresh) the connected account.
     *
     * <p>Deliberately NOT {@code @Transactional}: the first two statements are
     * round trips to Google, and wrapping them meant a pooled connection was
     * checked out for the whole exchange - up to fifty seconds against the
     * configured timeouts - to perform a single row write at the end. There is
     * nothing here to make atomic; the write is one idempotent upsert, and the
     * back-fill event now fires after that write has already committed rather
     * than after a transaction that also contained the network.
     */
    public GoogleStatusResponse connect(String code) {
        UUID adminId = adminId();
        GoogleOAuthClient.Tokens tokens = oauth.exchangeCode(code);
        String email = oauth.userEmail(tokens.accessToken());
        if (email == null || email.isBlank()) {
            throw new BusinessRuleException("تعذّر قراءة بريد حساب Google، حاول مرة أخرى");
        }
        GoogleAccount account = accountRepo.findByAdminIdAndEmail(adminId, email)
                .orElseGet(GoogleAccount::new);
        account.setAdminId(adminId);
        account.setEmail(email);
        account.setRefreshToken(tokens.refreshToken());
        account.setAccessToken(tokens.accessToken());
        account.setAccessExpiry(java.time.OffsetDateTime.now().plusSeconds(Math.max(60, tokens.expiresInSeconds() - 30)));
        accountRepo.save(account);
        // Back-fill every existing student automatically once the account is live
        // (after commit, background thread). No manual "sync now" needed.
        events.publishEvent(new GoogleContactEvents.AccountConnected(adminId));
        return status();
    }

    @Transactional
    public GoogleStatusResponse disconnect(UUID accountId) {
        UUID adminId = adminId();
        GoogleAccount account = accountRepo.findById(accountId)
                .filter(a -> a.getAdminId().equals(adminId))
                .orElseThrow(() -> new BusinessRuleException("الحساب غير موجود"));
        accountRepo.delete(account);
        return status();
    }

    @Transactional(readOnly = true)
    public List<GoogleMarkResponse> marks() {
        UUID adminId = adminId();
        Map<UUID, GradeContactMark> byGrade = markRepo.findByAdminId(adminId).stream()
                .collect(Collectors.toMap(GradeContactMark::getGradeId, m -> m, (a, b) -> a));
        List<GoogleMarkResponse> out = new java.util.ArrayList<>();
        for (Grade g : gradeRepo.findAllByOrderBySortOrderAscNameAsc()) {
            GradeContactMark m = byGrade.get(g.getId());
            out.add(new GoogleMarkResponse(g.getId(), g.getName(), g.isActive(),
                    m == null ? null : m.getStudentMark(),
                    m == null ? null : m.getParentMark(),
                    m == null ? null : m.getBothMark()));
        }
        return out;
    }

    @Transactional
    public GoogleMarkResponse setMark(UUID gradeId, GoogleMarkRequest req) {
        UUID adminId = adminId();
        Grade grade = gradeRepo.findById(gradeId)
                .orElseThrow(() -> new BusinessRuleException("الصف غير موجود"));
        GradeContactMark m = markRepo.findByAdminIdAndGradeId(adminId, gradeId).orElseGet(() -> {
            GradeContactMark fresh = new GradeContactMark();
            fresh.setAdminId(adminId);
            fresh.setGradeId(gradeId);
            return fresh;
        });
        m.setStudentMark(clean(req.studentMark()));
        m.setParentMark(clean(req.parentMark()));
        m.setBothMark(clean(req.bothMark()));
        markRepo.save(m);
        return new GoogleMarkResponse(grade.getId(), grade.getName(), grade.isActive(),
                m.getStudentMark(), m.getParentMark(), m.getBothMark());
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /**
     * Force a full re-sync of every student now; surfaces the first real error.
     *
     * <p>Reads ids only - loading the whole roster as entities to take the
     * primary key off each was pure waste. What this does NOT fix is that the
     * Google calls still happen inline, one student at a time, on the request
     * thread: a large roster is thousands of sequential round trips in a single
     * HTTP request, which will hit a proxy timeout long before it finishes and
     * invites the user to press the button again. Routing it through the outbox
     * (as {@code reconcile} already does) would fix that, but it changes what
     * the button can honestly report back - "queued" rather than "N contacts
     * written" - and that is a product decision, not a load fix.
     */
    public GoogleResyncResult resyncAll() {
        UUID adminId = adminId();
        List<UUID> ids = roster(adminId);
        int contacts = 0;
        for (UUID id : ids) {
            contacts += syncService.syncStudentThrowing(adminId, id);
        }
        return new GoogleResyncResult(ids.size(), contacts);
    }

    /** Most students one request will carry - see {@link GoogleResyncBatch}. */
    private static final int MAX_BATCH = 25;

    /**
     * Sync one slice of the roster and say how much is left.
     *
     * <p>This is the same work {@link #resyncAll()} does, cut into pieces the
     * caller drives: each call writes the contacts for a handful of students and
     * reports the roster size, so the screen can show how far it has come and the
     * request never runs long enough to be cut off in the middle.
     *
     * <p>The order is by student id, which does not change, so a slice boundary
     * means the same thing on the next call. A student added while a run is in
     * flight can shift what a later slice covers by one - the reconciliation job
     * picks them up regardless, and every write here is an idempotent upsert.
     */
    public GoogleResyncBatch resyncBatch(int offset, int limit) {
        UUID adminId = adminId();
        List<UUID> ids = roster(adminId);
        int total = ids.size();
        int from = Math.min(Math.max(offset, 0), total);
        int to = Math.min(total, from + Math.min(Math.max(limit, 1), MAX_BATCH));
        try {
            GoogleContactSyncService.AuditResult r = syncService.auditStudents(adminId, ids.subList(from, to));
            return new GoogleResyncBatch(total, to, r.ok(), r.updated(), r.created(), 0, to >= total);
        } catch (GoogleRateLimitException ex) {
            // Google's minute is full. Nothing here failed and nothing was lost:
            // the slice simply did not run, so the caller is told how long to
            // wait and asks for the SAME offset again.
            return new GoogleResyncBatch(total, from, 0, 0, 0, ex.retryAfterSeconds(), false);
        }
    }

    /** Every student's id, in a stable order, for a workspace with an account. */
    private List<UUID> roster(UUID adminId) {
        if (accountRepo.findByAdminIdOrderByEmailAsc(adminId).isEmpty()) {
            throw new BusinessRuleException("لا يوجد حساب Google مرتبط");
        }
        return studentRepository.findRoster(adminId).stream()
                .map(StudentRepository.RosterRow::getId)
                .sorted()
                .toList();
    }
}
