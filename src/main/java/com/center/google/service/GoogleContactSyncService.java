package com.center.google.service;
import com.center.common.exception.BusinessRuleException;
import com.center.google.event.GoogleContactEvents;
import com.center.google.client.GoogleOAuthClient;
import com.center.google.client.GooglePeopleClient;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import com.center.google.entity.GoogleAccount;
import com.center.google.entity.GoogleContactLink;
import com.center.google.entity.GradeContactMark;
import com.center.student.entity.Student;
import com.center.common.enums.LinkStatus;
import com.center.google.repository.GoogleAccountRepository;
import com.center.google.repository.GoogleContactLinkRepository;
import com.center.google.repository.GoogleContactsConfigRepository;
import com.center.google.repository.GradeContactMarkRepository;
import com.center.grade.repository.GradeRepository;
import com.center.parent.repository.ParentStudentLinkRepository;
import com.center.student.repository.StudentRepository;
import com.center.google.client.GooglePeopleClient.PersonRef;
import com.center.common.tenant.TenantContext;
import com.center.common.tenant.TenantScopedExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps Google Contacts in step with the system. Sync is student-driven: every
 * contact is named "{student full name} {role mark}" where the mark distinguishes
 * whether the number is the student's, the parent's, or both (same number). Phones
 * are saved without a country code. Runs after commit on a background thread so a
 * slow or failing Google call never blocks or rolls back the originating flow.
 *
 * <p>Before creating, an existing contact with the same phone is looked up (our
 * link table first, then a People API phone search) and renamed instead, so no
 * duplicates are created.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleContactSyncService {

    private static final String STUDENT = "student";
    private static final String PARENT = "parent";
    private static final String BOTH = "both";

    private final GoogleContactsConfigRepository configRepo;
    private final GoogleAccountRepository accountRepo;
    private final GradeContactMarkRepository markRepo;
    private final GoogleContactLinkRepository linkRepo;
    private final GradeRepository gradeRepo;
    private final StudentRepository studentRepository;
    private final ParentStudentLinkRepository parentLinkRepository;
    private final TenantScopedExecutor tenantTx;
    private final GoogleOAuthClient oauth;
    private final GooglePeopleClient people;

    // ---- event entry points (after commit, async) --------------------------

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onStudentChanged(GoogleContactEvents.StudentChanged e) {
        try {
            syncStudentInternal(e.adminId(), e.studentId(), false);
        } catch (Exception ex) {
            log.warn("Google sync for student {} failed: {}", e.studentId(), ex.getMessage());
        }
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onParentChanged(GoogleContactEvents.ParentChanged e) {
        try {
            for (var link : parentLinkRepository.findByParentIdAndStatus(e.parentId(), LinkStatus.APPROVED)) {
                try {
                    syncStudentInternal(link.getStudentAdminId(), link.getStudentId(), false);
                } catch (Exception ex) {
                    log.warn("Google sync for student {} (parent {}) failed: {}",
                            link.getStudentId(), e.parentId(), ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("Google sync for parent {} failed: {}", e.parentId(), ex.getMessage());
        }
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onAccountConnected(GoogleContactEvents.AccountConnected e) {
        try {
            List<UUID> ids = TenantContext.callAs(e.adminId(), () -> tenantTx.inTenantTx(
                    () -> studentRepository.findAll().stream().map(Student::getId).toList()));
            for (UUID id : ids) {
                try {
                    syncStudentInternal(e.adminId(), id, false);
                } catch (Exception ex) {
                    log.warn("Google back-fill for student {} failed: {}", id, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("Google back-fill for admin {} failed: {}", e.adminId(), ex.getMessage());
        }
    }

    /** Synchronous re-sync of one student that surfaces errors (manual "sync now"). */
    public int syncStudentThrowing(UUID adminId, UUID studentId) {
        return syncStudentInternal(adminId, studentId, true);
    }

    // ---- core sync ---------------------------------------------------------

    /** Snapshot of the fields a sync needs, read inside the tenant transaction. */
    private record Snapshot(String name, String grade, List<String> studentPhones, List<String> parentPhones) {}

    private int syncStudentInternal(UUID adminId, UUID studentId, boolean strict) {
        if (adminId == null || studentId == null) return 0;
        if (!isEnabled(adminId)) return 0;
        List<GoogleAccount> accounts = accountRepo.findByAdminIdOrderByEmailAsc(adminId);
        if (accounts.isEmpty()) return 0;

        Snapshot snap = loadSnapshot(adminId, studentId);
        if (snap == null || snap.name() == null || snap.name().isBlank()) return 0;

        // Classify each distinct phone by role: a number that is both a student
        // and a parent number gets the "both" mark.
        Map<String, String> studentPhones = normalise(snap.studentPhones());
        Map<String, String> parentPhones = normalise(snap.parentPhones());
        Map<String, String> phoneRole = new LinkedHashMap<>();
        for (String p : studentPhones.keySet()) phoneRole.put(p, STUDENT);
        for (String p : parentPhones.keySet()) {
            phoneRole.merge(p, PARENT, (existing, incoming) -> BOTH);
        }

        GradeContactMark mark = resolveMark(adminId, snap.grade());
        int written = 0;
        for (GoogleAccount account : accounts) {
            String token = accessToken(account);
            if (token == null) {
                if (strict) {
                    throw new com.center.common.exception.BusinessRuleException(
                            "تعذّر الاتصال بحساب Google (" + account.getEmail() + ") - أعد ربطه");
                }
                continue;
            }
            for (Map.Entry<String, String> pe : phoneRole.entrySet()) {
                try {
                    upsert(adminId, account.getId(), token, studentId, pe.getValue(),
                            pe.getKey(), contactName(snap.name(), mark, pe.getValue()));
                    written++;
                } catch (Exception ex) {
                    log.warn("Google upsert failed (account {}, phone {}): {}",
                            account.getEmail(), pe.getKey(), ex.getMessage());
                    if (strict) {
                        throw new com.center.common.exception.BusinessRuleException(
                                "تعذّر حفظ جهة الاتصال في Google: " + ex.getMessage());
                    }
                }
            }
        }
        return written;
    }

    private void upsert(UUID adminId, UUID accountId, String token, UUID studentId,
                        String role, String phone, String name) {
        Optional<GoogleContactLink> existing = linkRepo.findByGoogleAccountIdAndPhone(accountId, phone);
        if (existing.isPresent()) {
            GoogleContactLink link = existing.get();
            PersonRef pr = people.renameContact(token, link.getResourceName(), link.getEtag(), name);
            link.setEtag(pr.etag());
            link.setSubjectType(role);
            link.setSubjectId(studentId);
            linkRepo.save(link);
            return;
        }
        Optional<PersonRef> found = people.findByPhone(token, phone);
        PersonRef pr = found.isPresent()
                ? people.renameContact(token, found.get().resourceName(), found.get().etag(), name)
                : people.createContact(token, name, phone);
        GoogleContactLink link = new GoogleContactLink();
        link.setAdminId(adminId);
        link.setGoogleAccountId(accountId);
        link.setSubjectType(role);
        link.setSubjectId(studentId);
        link.setPhone(phone);
        link.setResourceName(pr.resourceName());
        link.setEtag(pr.etag());
        linkRepo.save(link);
    }

    // ---- helpers -----------------------------------------------------------

    private boolean isEnabled(UUID adminId) {
        return configRepo.findById(adminId).map(c -> c.isEnabled()).orElse(false);
    }

    private Snapshot loadSnapshot(UUID adminId, UUID studentId) {
        return TenantContext.callAs(adminId, () -> tenantTx.inTenantTx(() -> {
            Student s = studentRepository.findById(studentId).orElse(null);
            if (s == null) return null;
            return new Snapshot(s.getName(), s.getGrade(),
                    s.getStudentPhones() == null ? List.of() : List.of(s.getStudentPhones()),
                    s.getParentPhones() == null ? List.of() : List.of(s.getParentPhones()));
        }));
    }

    private GradeContactMark resolveMark(UUID adminId, String gradeName) {
        if (gradeName == null || gradeName.isBlank()) return null;
        return gradeRepo.findByName(gradeName.strip())
                .flatMap(g -> markRepo.findByAdminIdAndGradeId(adminId, g.getId()))
                .orElse(null);
    }

    private static String contactName(String studentName, GradeContactMark mark, String role) {
        String suffix = mark == null ? null : switch (role) {
            case STUDENT -> mark.getStudentMark();
            case PARENT -> mark.getParentMark();
            case BOTH -> mark.getBothMark();
            default -> null;
        };
        return suffix == null || suffix.isBlank() ? studentName : studentName + " " + suffix.strip();
    }

    /** Distinct local phones (country code stripped), preserving order. */
    private static Map<String, String> normalise(List<String> phones) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : phones) {
            String local = localPhone(raw);
            if (!local.isEmpty()) out.putIfAbsent(local, local);
        }
        return out;
    }

    /** Egyptian local form (no country code): "+20 10.." / "2010.." -> "010..". */
    static String localPhone(String raw) {
        String d = raw == null ? "" : raw.replaceAll("\\D", "");
        if (d.startsWith("20") && d.length() == 12) d = d.substring(2);
        if (!d.isEmpty() && !d.startsWith("0")) d = "0" + d;
        return d;
    }

    /** A valid access token for the account, refreshing (and persisting) if stale. */
    private String accessToken(GoogleAccount account) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean fresh = account.getAccessToken() != null
                && account.getAccessExpiry() != null
                && account.getAccessExpiry().isAfter(now.plusSeconds(60));
        if (fresh) return account.getAccessToken();
        try {
            GoogleOAuthClient.Tokens t = oauth.refresh(account.getRefreshToken());
            account.setAccessToken(t.accessToken());
            account.setAccessExpiry(now.plusSeconds(Math.max(60, t.expiresInSeconds() - 30)));
            accountRepo.save(account);
            return account.getAccessToken();
        } catch (Exception ex) {
            log.warn("Google token refresh failed for {}: {}", account.getEmail(), ex.getMessage());
            return null;
        }
    }

}
