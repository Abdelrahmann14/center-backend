package com.center.push.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.center.auth.security.AuthenticatedUser;
import com.center.push.dto.RegisterTokenRequest;
import com.center.push.service.PushService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Device push-token registration, available to every signed-in account. */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
@Tag(name = "Push")
public class PushController {

    private final PushService pushService;

    @PostMapping("/tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Register this device's Expo push token for the current account")
    public void register(@Valid @RequestBody RegisterTokenRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        pushService.register(principal.getId(), request.token(), request.platform());
    }

    @DeleteMapping("/tokens/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Forget a device's push token (e.g. on logout)")
    public void unregister(@PathVariable String token) {
        pushService.unregister(token);
    }
}
