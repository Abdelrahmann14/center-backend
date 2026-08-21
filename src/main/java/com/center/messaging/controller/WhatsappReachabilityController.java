package com.center.messaging.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.center.common.exception.BusinessRuleException;
import com.center.common.tenant.TenantContext;
import com.center.messaging.service.WhatsappReachabilityService;
import com.center.student.repository.StudentRepository;
import com.center.whatsapp.check.WhatsappNumberCheckService;
import com.center.whatsapp.check.WhatsappNumberCheckService.CheckResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Which numbers this workspace can reach on WhatsApp, and running the check that
 * finds out.
 *
 * <p>Its own controller rather than a method on the messages controller, because
 * the reader is the students page, not the messages page - a receptionist who
 * may look at students but may not send anything still needs to see that a
 * family cannot be reached.
 */
@RestController
@RequestMapping("/api/messaging/whatsapp")
@RequiredArgsConstructor
@Tag(name = "WhatsApp messaging")
public class WhatsappReachabilityController {

    private final WhatsappReachabilityService reachability;
    private final WhatsappNumberCheckService numberCheck;
    private final StudentRepository students;

    @GetMapping("/reachability")
    @PreAuthorize("hasAnyAuthority('PERM_STUDENT_VIEW','PERM_NOTIFICATION_SEND')")
    @Operation(summary = "Phone -> whether WhatsApp can reach it",
            description = "Merges the number check with this workspace's own delivery "
                    + "reports. A number that is absent has never been checked or "
                    + "messaged, which is not the same as unreachable.")
    public Map<String, Boolean> reachability() {
        return reachability.reachability();
    }

    /** How many roster numbers have never been checked, and whether we can. */
    public record CheckBacklog(int pending, String blockedReason) {
    }

    @GetMapping("/check-numbers/pending")
    @PreAuthorize("hasAnyAuthority('PERM_STUDENT_VIEW','PERM_NOTIFICATION_SEND')")
    @Operation(summary = "How many roster numbers have no WhatsApp check yet")
    public CheckBacklog pending() {
        List<String> phones = rosterPhones();
        return new CheckBacklog(numberCheck.unanswered(phones).size(),
                numberCheck.configured() ? null
                        : "خدمة فحص الأرقام غير مُفعّلة — بياناتها تُضبَط من إعدادات المنصة");
    }

    @PostMapping("/check-numbers")
    @PreAuthorize("hasAnyAuthority('PERM_STUDENT_VIEW','PERM_NOTIFICATION_SEND')")
    @Operation(summary = "Check the next batch of unchecked roster numbers",
            description = "Returns what this batch did and how many are left. Call again "
                    + "while remaining > 0 and the batch still checked something.")
    public CheckResult check() {
        return numberCheck.checkNext(rosterPhones());
    }

    /**
     * The workspace's own numbers. The check service and its cache are shared
     * across the platform - a number is a number - but a teacher may only ask it
     * about the families on THEIR roster, never enumerate someone else's.
     */
    private List<String> rosterPhones() {
        UUID admin = TenantContext.get();
        if (admin == null) {
            throw new BusinessRuleException("هذه الصفحة متاحة لحسابات المدرّسين فقط");
        }
        return students.allPhones(admin);
    }
}
