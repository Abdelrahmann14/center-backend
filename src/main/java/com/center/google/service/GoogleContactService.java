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
import com.center.google.dto.GoogleResyncResult;
import com.center.google.dto.GoogleStatusResponse;
import com.center.google.entity.GoogleAccount;
import com.center.grade.entity.Grade;
import com.center.google.entity.GradeContactMark;
import com.center.student.entity.Student;
import com.center.common.exception.BusinessRuleException;
import com.center.google.repository.GoogleAccountRepository;
import com.center.google.repository.GoogleContactsConfigRepository;
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

    private final GoogleContactsConfigRepository configRepo;
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

    private boolean enabled(UUID adminId) {
        return configRepo.findById(adminId).map(c -> c.isEnabled()).orElse(false);
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
        if (!enabled(adminId)) {
            throw new BusinessRuleException("مزامنة جهات اتصال Google غير مُفعّلة لحسابك");
        }
        return oauth.authUrl(UUID.randomUUID().toString());
    }

    /** Exchange the OAuth code and store (or refresh) the connected account. */
    @Transactional
    public GoogleStatusResponse connect(String code) {
        UUID adminId = adminId();
        if (!enabled(adminId)) {
            throw new BusinessRuleException("مزامنة جهات اتصال Google غير مُفعّلة لحسابك");
        }
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
        for (Grade g : gradeRepo.findAllByOrderByCreatedAtAsc()) {
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

    /** Force a full re-sync of every student now; surfaces the first real error. */
    public GoogleResyncResult resyncAll() {
        UUID adminId = adminId();
        if (!enabled(adminId)) {
            throw new BusinessRuleException("مزامنة جهات اتصال Google غير مُفعّلة لحسابك");
        }
        if (accountRepo.findByAdminIdOrderByEmailAsc(adminId).isEmpty()) {
            throw new BusinessRuleException("لا يوجد حساب Google مرتبط");
        }
        List<UUID> ids = studentRepository.findAll().stream().map(Student::getId).toList();
        int contacts = 0;
        for (UUID id : ids) {
            contacts += syncService.syncStudentThrowing(adminId, id);
        }
        return new GoogleResyncResult(ids.size(), contacts);
    }
}
