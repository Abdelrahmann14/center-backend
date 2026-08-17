package com.center.messaging.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.center.common.tenant.TenantContext;
import com.center.messaging.event.AttendanceRecordedEvent;
import com.center.messaging.event.StudentCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fires the automated attendance message the instant a registration commits - but
 * only when its (lecture, group) is opted in on the registration page. It runs
 * outside a request, so it binds the workspace tenant and opens a fresh transaction
 * before touching scoped data. Absence and exam grades are not automated at all:
 * both are sent from the lesson roster's own buttons, on the teacher's say-so.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WhatsappAutomationRunner {

    private final WhatsappMessagingService messagingService;

    /**
     * No {@code inTenantTx} here, unlike its two siblings: the service now opens
     * its own read transaction to decide and render, then sends outside it. A
     * transaction opened here would wrap the WhatsApp round trip again and hold a
     * pooled connection across it - exactly what the split removed, and this is
     * the listener that fires on every single registration.
     */
    @Async
    @TransactionalEventListener
    public void onAttendance(AttendanceRecordedEvent e) {
        if (e.adminId() == null) {
            return;
        }
        try {
            TenantContext.callAs(e.adminId(), () -> {
                messagingService.sendAttendanceOnRegister(e.studentId(), e.groupId(), e.lectureId());
                return null;
            });
        } catch (RuntimeException ex) {
            log.warn("Attendance message failed for student {}: {}", e.studentId(), ex.getMessage());
        }
    }

    /**
     * No {@code inTenantTx}, for the same reason as {@link #onAttendance}: the
     * service now opens its own read transaction to decide and render, then does
     * the PDF render and the WhatsApp upload outside it. A transaction opened
     * here would wrap both again and undo the split.
     */
    @Async
    @TransactionalEventListener
    public void onStudentCreated(StudentCreatedEvent e) {
        if (e.adminId() == null) {
            return;
        }
        try {
            TenantContext.callAs(e.adminId(), () -> {
                messagingService.sendNewStudent(e.studentId());
                return null;
            });
        } catch (RuntimeException ex) {
            log.warn("New-student message failed for student {}: {}", e.studentId(), ex.getMessage());
        }
    }

}
