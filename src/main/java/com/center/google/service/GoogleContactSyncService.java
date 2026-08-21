package com.center.google.service;
import com.center.common.exception.BusinessRuleException;
import com.center.google.event.GoogleContactEvents;
import com.center.google.client.GoogleOAuthClient;
import com.center.google.client.GooglePeopleClient;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import com.center.google.entity.GoogleAccount;
import com.center.google.entity.GoogleContactLink;
import com.center.google.entity.GradeContactMark;
import com.center.student.entity.Student;
import com.center.google.repository.GoogleAccountRepository;
import com.center.google.repository.GoogleContactLinkRepository;
import com.center.google.repository.GoogleContactsConfigRepository;
import com.center.google.repository.GradeContactMarkRepository;
import com.center.grade.repository.GradeRepository;
import com.center.student.repository.StudentRepository;
import com.center.google.client.GooglePeopleClient.PersonRef;
import com.center.common.tenant.TenantContext;
import com.center.common.tenant.TenantScopedExecutor;
import com.center.outbox.entity.ExternalEffect;
import com.center.outbox.service.ExternalEffectOutbox;

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
    private final TenantScopedExecutor tenantTx;
    private final GoogleOAuthClient oauth;
    private final GooglePeopleClient people;
    private final ExternalEffectOutbox outbox;

    // ---- event entry points (after commit, async) --------------------------
    //
    // These no longer call Google themselves. Writing a contact needs the
    // internet, and a listener that simply tries once has no answer for "the
    // line was down": the call failed, the exception was logged, and the contact
    // was never written - a student added offline would reach the database on
    // reconnect and still never reach Google. Each change is queued instead, and
    // the outbox drainer completes it the moment the line allows, retrying with
    // backoff for as long as it takes.

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onStudentChanged(GoogleContactEvents.StudentChanged e) {
        queue(e.adminId(), e.studentId());
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onAccountConnected(GoogleContactEvents.AccountConnected e) {
        try {
            // Ids only. This fires once per account link, but it fires over the
            // WHOLE roster, and loading every student as an entity just to read
            // the primary key is the biggest single allocation the app can make.
            for (var row : studentRepository.findRoster(e.adminId())) {
                queue(e.adminId(), row.getId());
            }
        } catch (Exception ex) {
            log.warn("Google back-fill for admin {} failed: {}", e.adminId(), ex.getMessage());
        }
    }

    /** Promise Google this student, for the drainer to keep. */
    private void queue(UUID adminId, UUID studentId) {
        if (adminId == null || studentId == null || !isEnabled(adminId)) {
            return;
        }
        outbox.enqueue(adminId, ExternalEffect.GOOGLE_CONTACT, studentId);
    }

    /** Synchronous re-sync of one student that surfaces errors (manual "sync now"). */
    public int syncStudentThrowing(UUID adminId, UUID studentId) {
        return syncStudentInternal(adminId, studentId, true);
    }

    // ---- audit ("check, then repair only what is wrong") --------------------

    /**
     * What one audit slice found.
     *
     * @param ok      numbers already in Google under the right name - untouched
     * @param updated numbers found under a different name and renamed
     * @param created numbers Google did not have at all
     */
    public record AuditResult(int ok, int updated, int created) {}

    /**
     * Check a set of students against Google and repair only the differences.
     *
     * <p>This is what the manual "sync all numbers" button runs, and it is
     * deliberately NOT the same thing as re-syncing every student: re-syncing
     * rewrites every contact whether or not anything changed, which is a write
     * per number for a roster that is usually already correct - the surest way to
     * spend Google's per-minute quota on work with no effect.
     *
     * <p>Instead the whole address book is read once (a thousand contacts per
     * request), and each of the system's numbers is looked up in it: present with
     * the right name is left alone, present under a different name is renamed,
     * missing is created. A second run over an unchanged roster therefore writes
     * nothing at all.
     */
    public AuditResult auditStudents(UUID adminId, List<UUID> studentIds) {
        if (adminId == null || studentIds.isEmpty() || !isEnabled(adminId)) {
            return new AuditResult(0, 0, 0);
        }
        List<GoogleAccount> accounts = accountRepo.findByAdminIdOrderByEmailAsc(adminId);
        if (accounts.isEmpty()) {
            return new AuditResult(0, 0, 0);
        }

        int ok = 0;
        int updated = 0;
        int created = 0;
        for (GoogleAccount account : accounts) {
            String token = accessToken(account);
            if (token == null) {
                throw new BusinessRuleException(
                        "تعذّر الاتصال بحساب Google (" + account.getEmail() + ") - أعد ربطه");
            }
            // One read for the account, not one per number.
            Map<String, GooglePeopleClient.Contact> byPhone = new HashMap<>();
            for (GooglePeopleClient.Contact c : people.listContacts(token)) {
                for (String phone : c.phones()) {
                    byPhone.putIfAbsent(GooglePeopleClient.phoneKey(phone), c);
                }
            }

            for (UUID studentId : studentIds) {
                Snapshot snap = loadSnapshot(adminId, studentId);
                if (snap == null || snap.name() == null || snap.name().isBlank()) {
                    continue;
                }
                GradeContactMark mark = resolveMark(adminId, snap.grade());
                for (Map.Entry<String, String> pe : phoneRoles(snap).entrySet()) {
                    String phone = pe.getKey();
                    String wanted = contactName(snap.name(), mark, pe.getValue());
                    GooglePeopleClient.Contact existing = byPhone.get(GooglePeopleClient.phoneKey(phone));
                    if (existing == null) {
                        PersonRef pr = people.createContact(token, wanted, phone);
                        saveLink(adminId, account.getId(), studentId, pe.getValue(), phone, pr, wanted);
                        created++;
                    } else if (wanted.equals(existing.name()) || alreadyWritten(account.getId(), phone, wanted)) {
                        // Either Google holds exactly this name, or this system
                        // already sent exactly this name and Google chose to store
                        // it differently (it rebuilds a display name from the parts
                        // it parses). Both mean there is nothing left to do -
                        // rewriting the second case is how a run kept reporting the
                        // same a few "corrections" every single time.
                        saveLink(adminId, account.getId(), studentId, pe.getValue(), phone,
                                new PersonRef(existing.resourceName(), existing.etag()), wanted);
                        ok++;
                    } else {
                        PersonRef pr = people.renameContact(token, existing.resourceName(),
                                existing.etag(), wanted);
                        saveLink(adminId, account.getId(), studentId, pe.getValue(), phone, pr, wanted);
                        updated++;
                    }
                }
            }
        }
        return new AuditResult(ok, updated, created);
    }

    /** Each distinct phone of a student, mapped to whose number it is. */
    private static Map<String, String> phoneRoles(Snapshot snap) {
        Map<String, String> phoneRole = new LinkedHashMap<>();
        for (String p : normalise(snap.studentPhones()).keySet()) {
            phoneRole.put(p, STUDENT);
        }
        for (String p : normalise(snap.parentPhones()).keySet()) {
            phoneRole.merge(p, PARENT, (existing, incoming) -> BOTH);
        }
        return phoneRole;
    }

    /** Create or refresh the row that ties one phone to one Google contact. */
    private void saveLink(UUID adminId, UUID accountId, UUID studentId, String role,
            String phone, PersonRef pr, String writtenName) {
        GoogleContactLink link = linkRepo.findByGoogleAccountIdAndPhone(accountId, phone)
                .orElseGet(GoogleContactLink::new);
        link.setAdminId(adminId);
        link.setGoogleAccountId(accountId);
        link.setSubjectType(role);
        link.setSubjectId(studentId);
        link.setPhone(phone);
        link.setResourceName(pr.resourceName());
        link.setEtag(pr.etag());
        link.setDisplayName(writtenName);
        linkRepo.save(link);
    }

    /** True when this exact name has already been sent to Google for this number. */
    private boolean alreadyWritten(UUID accountId, String phone, String wanted) {
        return linkRepo.findByGoogleAccountIdAndPhone(accountId, phone)
                .map(l -> wanted.equals(l.getDisplayName()))
                .orElse(false);
    }

    // ---- reconciliation ----------------------------------------------------

    /** What one reconciliation pass did, for the log. */
    public record ReconcileResult(int created, int refreshed, int removed) {
        boolean isEmpty() {
            return created == 0 && refreshed == 0 && removed == 0;
        }
    }

    /**
     * Bring one workspace's Google contacts back in line with its roster.
     *
     * <p>The event listeners above are the fast path, but they are fire-and-forget:
     * a student saved while Google was unreachable, or while the access token had
     * expired, loses its event and is never retried. Nothing in the system noticed
     * - the contact simply never appeared. This pass is what notices.
     *
     * <p>Three differences are repaired: students never synced at all, students
     * changed since the window opened (an edit whose event was lost), and links
     * whose student no longer exists (a deletion, which had NO handling at all -
     * deleted students stayed in Google Contacts forever).
     *
     * <p>Errors propagate rather than being swallowed. A pass that hits a dead
     * token has, by definition, stopped being able to tell "missing" from
     * "unreachable", and carrying on would delete contacts on that basis.
     */
    public ReconcileResult reconcile(UUID adminId, OffsetDateTime changedSince) {
        if (!isEnabled(adminId) || accountRepo.findByAdminIdOrderByEmailAsc(adminId).isEmpty()) {
            return new ReconcileResult(0, 0, 0);
        }

        // (id, updatedAt) instead of the whole entity. This runs every ten
        // minutes for every connected workspace, and hydrating a full roster of
        // managed entities to read two fields off each one was both the largest
        // recurring allocation in the process and a read whose cost grew with
        // every column ever added to the student record.
        List<StudentRepository.RosterRow> roster = studentRepository.findRoster(adminId);
        Set<UUID> live = roster.stream().map(StudentRepository.RosterRow::getId)
                .collect(Collectors.toSet());
        Set<UUID> synced = new HashSet<>(linkRepo.findSyncedSubjectIds(adminId));

        int created = 0;
        int refreshed = 0;
        for (StudentRepository.RosterRow s : roster) {
            boolean isNew = !synced.contains(s.getId());
            boolean edited = s.getUpdatedAt() != null && s.getUpdatedAt().isAfter(changedSince);
            if (!isNew && !edited) {
                continue;
            }
            // Queued, not written here: the drainer owns every outbound call, so
            // one unreachable moment defers the work instead of aborting the pass.
            outbox.enqueue(adminId, ExternalEffect.GOOGLE_CONTACT, s.getId());
            if (isNew) {
                created++;
            } else {
                refreshed++;
            }
        }

        int removed = removeOrphans(adminId, live);
        return new ReconcileResult(created, refreshed, removed);
    }

    /**
     * Drop the Google contacts of students that no longer exist here. Every link
     * carries a student id as its subject, so absence from the roster is enough.
     *
     * <p>Capped per pass. A workspace that clears out a graduating year hands
     * this method hundreds of orphans at once, and each one is a sequential HTTP
     * DELETE to Google - previously unbounded, on the shared scheduler, with a
     * 20-second read timeout apiece. The job repeats every ten minutes, so a
     * capped pass is not lost work; it is the same work spread over passes, with
     * a known worst-case duration for each.
     */
    private static final int MAX_ORPHAN_DELETES_PER_PASS = 100;

    private int removeOrphans(UUID adminId, Set<UUID> live) {
        List<GoogleContactLink> orphans = linkRepo.findByAdminId(adminId).stream()
                .filter(l -> !live.contains(l.getSubjectId()))
                .limit(MAX_ORPHAN_DELETES_PER_PASS)
                .toList();
        if (orphans.isEmpty()) {
            return 0;
        }
        Map<UUID, String> tokens = new HashMap<>();
        int removed = 0;
        for (GoogleContactLink link : orphans) {
            String token = tokens.computeIfAbsent(link.getGoogleAccountId(), id ->
                    accountRepo.findById(id).map(this::accessToken).orElse(null));
            if (token == null) {
                // Cannot reach the account that owns this contact. Leave the link
                // alone: dropping it here would strand the contact in Google with
                // nothing left pointing at it.
                continue;
            }
            people.deleteContact(token, link.getResourceName());
            linkRepo.delete(link);
            removed++;
        }
        return removed;
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
        Map<String, String> phoneRole = phoneRoles(snap);

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
                } catch (com.center.google.client.GoogleRateLimitException ex) {
                    // The quota, not the request. Let it travel: the caller can
                    // wait and resume, which turning it into a generic failure
                    // would take away.
                    throw ex;
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
            if (name.equals(link.getDisplayName())) {
                // Nothing about this contact changed. A student is re-synced on
                // every save - a group move, a note, a price - and renaming the
                // contact to the name it already carries is a write bought for
                // nothing, out of a per-minute quota that is easy to exhaust.
                return;
            }
            PersonRef pr = people.renameContact(token, link.getResourceName(), link.getEtag(), name);
            link.setEtag(pr.etag());
            link.setSubjectType(role);
            link.setSubjectId(studentId);
            link.setDisplayName(name);
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
        link.setDisplayName(name);
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

    /**
     * Right-to-left isolate. The mark is written AFTER the name, which is where
     * an Arabic reader expects to find it - on the LEFT of the name - but where
     * it actually lands depends on the paragraph direction of whatever is drawing
     * the contact, and a phone in an English locale draws it left-to-right: the
     * Arabic name is one right-to-left run placed at the left edge and the mark
     * ends up on the RIGHT of it, which is the wrong side.
     *
     * <p>Wrapping the whole name in an isolate fixes the direction to RTL for
     * this string alone, so the reading order is the same everywhere: name on the
     * right, mark on its left, whether the mark is an emoji, Arabic, Latin or
     * digits (a Latin mark is a left-to-right island INSIDE the RTL run, which
     * still puts it to the left of the name - without the isolate it would break
     * out of the run entirely and jump to the other side).
     */
    private static final String RTL_ISOLATE = "⁧";
    private static final String POP_ISOLATE = "⁩";

    private static String contactName(String studentName, GradeContactMark mark, String role) {
        String suffix = mark == null ? null : switch (role) {
            case STUDENT -> mark.getStudentMark();
            case PARENT -> mark.getParentMark();
            case BOTH -> mark.getBothMark();
            default -> null;
        };
        if (suffix == null || suffix.isBlank()) {
            // No mark, nothing to keep on a side: the name is left exactly as it
            // is, so a contact without a mark carries no invisible characters.
            return studentName;
        }
        return RTL_ISOLATE + studentName + " " + suffix.strip() + POP_ISOLATE;
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
