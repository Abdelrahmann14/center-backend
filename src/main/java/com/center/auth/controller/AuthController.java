package com.center.auth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;

import com.center.auth.dto.LoginRequest;
import com.center.admin.dto.VerifyAdminRequest;
import com.center.auth.dto.AuthenticatedUserResponse;
import com.center.auth.dto.LoginResponse;
import com.center.auth.security.AuthenticatedUser;
import com.center.auth.security.LoginRateLimiter;
import com.center.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;

    /**
     * The rate limit lives here rather than in the service because only the web
     * layer knows who is calling. A wrong password counts; anything else (a
     * disabled account, a server fault) is not the caller guessing, so it does
     * not.
     */
    @PostMapping("/login")
    @Operation(summary = "Sign in")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = rateLimiter.clientIp(http);
        String identity = request.email();
        rateLimiter.checkAllowed(identity, ip);
        try {
            LoginResponse response = authService.login(request);
            rateLimiter.recordSuccess(identity, ip);
            return response;
        } catch (BadCredentialsException ex) {
            rateLimiter.recordFailure(identity, ip);
            throw ex;
        }
    }

    @GetMapping("/me")
    @Operation(summary = "The account behind the current token - restores a stored session")
    public AuthenticatedUserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.me(principal.getId());
    }

    /** Also a password check, so it is guarded the same way as the login. */
    @PostMapping("/verify-admin")
    @Operation(summary = "Confirm the admin password to gate a sensitive change")
    public Map<String, Boolean> verifyAdmin(
            @Valid @RequestBody VerifyAdminRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest http) {
        String ip = rateLimiter.clientIp(http);
        String identity = "verify-admin:" + principal.getId();
        rateLimiter.checkAllowed(identity, ip);
        try {
            authService.verifyAdminPassword(request.password());
            rateLimiter.recordSuccess(identity, ip);
            return Map.of("ok", true);
        } catch (BadCredentialsException ex) {
            rateLimiter.recordFailure(identity, ip);
            throw ex;
        }
    }
}
