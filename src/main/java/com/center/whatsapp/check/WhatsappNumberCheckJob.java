package com.center.whatsapp.check;

import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.center.messaging.event.StudentCreatedEvent;
import com.center.student.repository.StudentRepository;
import com.center.whatsapp.check.WhatsappNumberCheckService.CheckResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Works through the roster asking "is this number on WhatsApp", a few at a time,
 * for as long as there are numbers nobody has asked about.
 *
 * <p>This used to be a button on the students page: a person pressed it and sat
 * through a loop of batches. That is the wrong shape for the work. The answer is
 * needed when someone is READING the students table, not when they remember to
 * go and fetch it, and the roster grows a few students at a time - so the check
 * should trickle in the background and simply be there.
 *
 * <p>Two triggers, and between them nothing is missed:
 *
 * <ul>
 *   <li>a student is created - their numbers are asked about immediately, so the
 *       row is already marked by the time anyone looks at it;</li>
 *   <li>a sweep every {@value #EVERY}, which catches everything else - a number
 *       edited on an existing student, a create whose event was lost because the
 *       check service was down, and the whole backlog of a roster that existed
 *       before any of this did.</li>
 * </ul>
 *
 * <p>An edited number needs no special handling: the store is keyed by the
 * NUMBER, so changing a student's phone produces one nobody has an answer for
 * and the sweep takes it from there. A number is therefore asked about exactly
 * once in its life.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsappNumberCheckJob {

    private static final String EVERY = "PT15M";

    /**
     * Numbers asked about across ALL workspaces in one sweep.
     *
     * <p>The per-workspace batch bounds one teacher; this bounds the platform.
     * Without it, a hundred workspaces each taking a batch would fire a hundred
     * batches at Green in one tick and get the account rate limited - the sweep
     * would then achieve nothing while looking busy. Whatever is left over is
     * simply the next tick's work; there is no deadline on this.
     */
    private static final int SWEEP_CAP = 200;

    private final StudentRepository students;
    private final WhatsappNumberCheckService checks;

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = EVERY)
    public void sweep() {
        // Not an error and not worth a log line every 15 minutes: the check is
        // optional, and a platform that never configured it has simply not
        // bought this feature.
        if (!checks.configured()) {
            return;
        }

        List<UUID> workspaces;
        try {
            workspaces = students.allWorkspaces();
        } catch (RuntimeException ex) {
            log.warn("number-check sweep could not list workspaces: {}", ex.getMessage());
            return;
        }

        int spent = 0;
        for (UUID adminId : workspaces) {
            if (spent >= SWEEP_CAP) {
                log.info("number-check sweep hit its cap of {}; the rest waits for the next tick",
                        SWEEP_CAP);
                return;
            }
            try {
                CheckResult r = checks.checkNext(students.allPhones(adminId));
                spent += r.checked() + r.failed();
                if (r.checked() > 0 || r.failed() > 0) {
                    log.info("number-check [{}]: {} answered, {} unanswerable, {} left",
                            adminId, r.checked(), r.failed(), r.remaining());
                }
            } catch (RuntimeException ex) {
                // One workspace failing must not end the sweep. Nothing is half
                // done - each number is stored in its own transaction - so the
                // next tick simply resumes where this left off.
                log.warn("number-check [{}] stopped: {}", adminId, ex.getMessage());
            }
        }
    }

    /**
     * The new student's own numbers, asked about as soon as the insert commits.
     *
     * <p>{@code @Async} because a Green round trip has no business inside the
     * request that created the student, and after the commit rather than before
     * so a rolled-back create never spends a check.
     */
    @Async
    @TransactionalEventListener
    public void onStudentCreated(StudentCreatedEvent e) {
        if (!checks.configured() || e.studentId() == null) {
            return;
        }
        try {
            // Addressed by id, and the store is not tenant-scoped, so this needs
            // no tenant bound - which is just as well, since there is none on
            // this thread.
            checks.checkNext(students.phonesOf(e.studentId()));
        } catch (RuntimeException ex) {
            log.warn("number check for new student {} failed: {}", e.studentId(), ex.getMessage());
        }
    }
}
