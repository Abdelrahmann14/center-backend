package com.center.account.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.center.account.dto.EmailAvailabilityResponse;
import com.center.common.enums.Role;
import com.center.account.service.AccountEmailService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Login-name availability for the internal creation forms (admins created by the
 * super admin, assistants created by an admin). Students use the public
 * equivalent on {@code /api/register}.
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts")
public class AccountEmailController {

    private final AccountEmailService accountEmailService;

    @GetMapping("/email-available")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Is this login name free for the given role?")
    public EmailAvailabilityResponse available(@RequestParam("username") String username,
            @RequestParam("role") Role role) {
        return accountEmailService.check(username, role);
    }
}
