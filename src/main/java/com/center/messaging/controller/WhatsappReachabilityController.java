package com.center.messaging.controller;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.center.messaging.service.WhatsappReachabilityService;
import com.center.whatsapp.check.WhatsappNumberCheckService;

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
 *
 * <p>Read-only. Running the check was a button here once; it is a background
 * job now ({@code WhatsappNumberCheckJob}), so there is nothing left to POST.
 */
@RestController
@RequestMapping("/api/messaging/whatsapp")
@RequiredArgsConstructor
@Tag(name = "WhatsApp messaging")
public class WhatsappReachabilityController {

    private final WhatsappReachabilityService reachability;
    private final WhatsappNumberCheckService numberCheck;

    @GetMapping("/reachability")
    // REGISTRATION_ACCESS is here for the attendance desk, which is where a
    // wrong number is actually found: the student is standing at the counter and
    // can be asked. An assistant who may register but may not browse the roster
    // was the one person who could fix it and the only one not being told.
    @PreAuthorize("hasAnyAuthority('PERM_STUDENT_VIEW','PERM_NOTIFICATION_SEND',"
            + "'PERM_REGISTRATION_ACCESS')")
    @Operation(summary = "Phone -> whether WhatsApp can reach it",
            description = "Merges the number check with this workspace's own delivery "
                    + "reports. A number that is absent has never been checked or "
                    + "messaged, which is not the same as unreachable.")
    public Map<String, Boolean> reachability() {
        return reachability.reachability();
    }

    /**
     * One number, answered while somebody is typing it into a form.
     *
     * @param exists true, false, or null for "could not answer" - the service is
     *               off, or Green did not reply. Null must be shown as unknown:
     *               telling a receptionist a number has no WhatsApp because a
     *               third party timed out is worse than telling them nothing
     */
    public record NumberCheckResponse(String phone, Boolean exists) {
    }

    @GetMapping("/check-number")
    @PreAuthorize("hasAnyAuthority('PERM_STUDENT_CREATE','PERM_STUDENT_UPDATE',"
            + "'PERM_STUDENT_VIEW','PERM_NOTIFICATION_SEND')")
    @Operation(summary = "Is this one number on WhatsApp",
            description = "Answered from the shared store when it is known, which costs "
                    + "nothing; only a number nobody has ever asked about reaches Green. "
                    + "Safe to call as a field is typed.")
    public NumberCheckResponse checkNumber(@RequestParam String phone) {
        return new NumberCheckResponse(phone, numberCheck.lookup(phone).orElse(null));
    }
}
