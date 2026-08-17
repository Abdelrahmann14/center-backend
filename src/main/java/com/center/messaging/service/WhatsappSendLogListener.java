package com.center.messaging.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.center.auth.security.AuthenticatedUser;
import com.center.common.tenant.TenantContext;
import com.center.messaging.entity.WhatsappMessageLog;
import com.center.messaging.repository.WhatsappMessageLogRepository;
import com.center.parent.repository.ParentRepository;
import com.center.student.repository.StudentRepository;
import com.center.user.repository.UserRepository;
import com.center.whatsapp.service.WhatsappSendEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Records every direct WhatsApp send in {@code wa_message_log}, so the Messages
 * history shows every message that left the system - verification codes, exam
 * results, parent-link notices, broadcasts, and the barcode/report/invoice PDFs -
 * not only the ones sent through the messaging feature itself.
 *
 * <p>These sends reach Green API with a phone number and nothing else, and the
 * log used to store exactly that: the recipient's name, code and type were left
 * empty and the history table rendered three dashes, as if the system had no
 * idea who it had just messaged. It always did - the number identifies the
 * person. This listener looks the number up (student first, then guardian, then
 * parent account) and records who was actually written to, so a row is only
 * anonymous when the number genuinely belongs to nobody on the roster.
 *
 * <p>It writes in its own {@code REQUIRES_NEW} transaction (so a read-only caller
 * like the barcode send can still record the row, and the row survives even when
 * the caller's own transaction later rolls back on a send failure), and it never
 * lets a logging failure break the actual send - the whole write is best-effort.
 */
// No @RequiredArgsConstructor here: the constructor below is written by hand
// because the TransactionTemplate has to be built and configured from the
// transaction manager, not injected. Lombok would add a SECOND constructor, and
// Spring refuses to guess between two.
@Component
@Slf4j
public class WhatsappSendLogListener {

    /** Purposes that a person triggers by hand; everything else is a system send. */
    private static final Set<String> MANUAL = Set.of("BARCODE", "REPORT", "INVOICE", "broadcast");

    /** Purposes addressed to the teacher's own number rather than to a family. */
    private static final Set<String> TO_TEACHER = Set.of("INVOICE");

    /**
     * Purposes whose body carries a one-time code or reset link. The send is still
     * recorded (type, status, time), but the secret itself is never stored - a
     * persistent, browsable log is the wrong place for it.
     */
    private static final Set<String> SECRET =
            Set.of("student_verification", "student_password_reset", "parent_password_reset");

    private final WhatsappMessageLogRepository logRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate tx;

    public WhatsappSendLogListener(WhatsappMessageLogRepository logRepository,
            StudentRepository studentRepository, ParentRepository parentRepository,
            UserRepository userRepository, PlatformTransactionManager txManager) {
        this.logRepository = logRepository;
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
        this.userRepository = userRepository;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @EventListener
    public void onSend(WhatsappSendEvent e) {
        // Resolved OUTSIDE the write transaction: the lookups are reads, and the
        // tenant/security context they need belongs to the calling thread.
        Recipient who = resolve(e);
        UUID byUser = actingUserId();
        String byName = actingUserName();
        try {
            tx.executeWithoutResult(status -> {
                WhatsappMessageLog row = new WhatsappMessageLog();
                row.setPhone(e.phone());
                row.setRecipientName(who.name());
                row.setRecipientCode(who.code());
                row.setRecipientType(who.type());
                row.setStudentId(who.studentId());
                row.setBody(bodyFor(e));
                row.setStatus(e.sent() ? "SENT" : "FAILED");
                row.setFailureReason(e.failureReason());
                row.setOrigin(e.purpose() == null ? "-" : e.purpose());
                row.setSource(MANUAL.contains(e.purpose()) ? "MANUAL" : "SYSTEM");
                row.setSentByUserId(byUser);
                row.setSentByName(byName);
                logRepository.save(row);
            });
        } catch (RuntimeException ex) {
            // A logging failure must never break the send it was recording.
            log.warn("Could not log WhatsApp send to {}: {}", e.phone(), ex.getMessage());
        }
    }

    /** Whoever a message went to: a name, a code, and what they are to us. */
    private record Recipient(String name, String code, String type, UUID studentId) {}

    /**
     * Identify the recipient from the number. The event may already carry an
     * explicit subject (the document sends know their student), which always
     * wins; otherwise the roster is searched.
     */
    private Recipient resolve(WhatsappSendEvent e) {
        UUID tenant = TenantContext.get();
        String phone = localPhone(e.phone());

        if (e.studentId() != null && tenant != null) {
            var s = studentRepository.findById(e.studentId()).orElse(null);
            if (s != null) {
                String type = phoneIn(s.getStudentPhones(), phone) ? "STUDENT" : "PARENT";
                return new Recipient(s.getName(), serial(s.getSerial()), type, s.getId());
            }
        }

        if (tenant != null && !phone.isEmpty()) {
            var match = studentRepository.findByAnyPhone(tenant, phone).orElse(null);
            if (match != null) {
                return new Recipient(match.getName(), serial(match.getSerial()),
                        match.getRole(), match.getId());
            }
        }

        // Not on the roster: a parent account signing in with its own number.
        if (!phone.isEmpty()) {
            var parent = parentRepository.findFirstByPhone(phone).orElse(null);
            if (parent != null) {
                return new Recipient(parent.getName(), serial(parent.getSerial()), "PARENT", null);
            }
        }

        // The invoice goes to the teacher who owns the workspace, not to a family.
        if (TO_TEACHER.contains(e.purpose()) && tenant != null) {
            String name = userRepository.findById(tenant).map(u -> u.getUsername()).orElse(null);
            return new Recipient(name, null, "TEACHER", null);
        }

        return new Recipient(null, null, "OTHER", null);
    }

    private static boolean phoneIn(String[] phones, String phone) {
        if (phones == null || phone.isEmpty()) {
            return false;
        }
        for (String p : phones) {
            if (phone.equals(localPhone(p))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The local Egyptian form the roster stores, so a number written as
     * "+20 10..." or "2010..." still matches "010...".
     */
    static String localPhone(String raw) {
        String d = raw == null ? "" : raw.replaceAll("\\D", "");
        if (d.startsWith("20") && d.length() == 12) {
            d = d.substring(2);
        }
        if (!d.isEmpty() && !d.startsWith("0")) {
            d = "0" + d;
        }
        return d;
    }

    private static String serial(Integer serial) {
        return serial == null ? null : String.valueOf(serial);
    }

    private static AuthenticatedUser principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedUser u ? u : null;
    }

    /** The signed-in account that caused this send, when there is one. */
    private static UUID actingUserId() {
        AuthenticatedUser u = principal();
        return u == null ? null : u.getId();
    }

    private static String actingUserName() {
        AuthenticatedUser u = principal();
        return u == null ? null : u.getUsername();
    }

    /** The stored body: a placeholder for secret-bearing sends, the text otherwise. */
    private static String bodyFor(WhatsappSendEvent e) {
        if (SECRET.contains(e.purpose())) {
            return "🔒 رسالة تحقق (المحتوى غير مخزَّن)";
        }
        return e.body() == null || e.body().isBlank() ? "-" : e.body();
    }
}
