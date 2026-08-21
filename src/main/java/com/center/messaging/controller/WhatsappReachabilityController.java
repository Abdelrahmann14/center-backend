package com.center.messaging.controller;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.center.messaging.service.WhatsappReachabilityService;

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

    @GetMapping("/reachability")
    @PreAuthorize("hasAnyAuthority('PERM_STUDENT_VIEW','PERM_NOTIFICATION_SEND')")
    @Operation(summary = "Phone -> whether WhatsApp can reach it",
            description = "Merges the number check with this workspace's own delivery "
                    + "reports. A number that is absent has never been checked or "
                    + "messaged, which is not the same as unreachable.")
    public Map<String, Boolean> reachability() {
        return reachability.reachability();
    }

}
