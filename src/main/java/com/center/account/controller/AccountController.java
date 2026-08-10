package com.center.account.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.account.dto.ChangePasswordRequest;
import com.center.account.dto.ChangePhoneRequest;
import com.center.account.dto.AccountResponse;
import com.center.auth.security.AuthenticatedUser;
import com.center.account.service.AccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The unified account page - one endpoint set for every role. */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@Tag(name = "Account")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "The signed-in account's own details")
    public AccountResponse account(@AuthenticationPrincipal AuthenticatedUser principal) {
        return accountService.get(principal);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change the account's own password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        accountService.changePassword(request, principal);
    }

    @PostMapping("/phone")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change the account's own phone")
    public void changePhone(@Valid @RequestBody ChangePhoneRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        accountService.changePhone(request, principal);
    }
}
