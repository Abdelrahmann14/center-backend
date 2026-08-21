package com.center.whatsapp.cloud.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.center.common.config.ApplicationProperties;
import com.center.whatsapp.cloud.service.WhatsappWebhookService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Where Meta reports what happened to the messages we sent, and anything a
 * customer sends back.
 *
 * <p>Unauthenticated by necessity - Meta cannot present a JWT - so the request
 * itself must prove it came from Meta. Two different proofs, one per verb:
 *
 * <ul>
 *   <li><b>GET</b> is the one-off handshake when the URL is saved in the app
 *       dashboard. Meta sends a token we chose; echoing back its challenge only
 *       when the token matches is what tells Meta the URL is really ours.</li>
 *   <li><b>POST</b> carries the events, signed with the app secret. Every body is
 *       verified before it is looked at; without that check this endpoint would
 *       apply whatever anyone on the internet posted at it.</li>
 * </ul>
 *
 * <p>Answers 200 to anything it accepted, including events it does not act on.
 * Meta retries on a non-200 with growing backoff and eventually disables the
 * subscription, so failing loudly here would cost the delivery reports entirely.
 */
@RestController
@RequestMapping("/api/whatsapp/webhook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WhatsApp Cloud API")
public class WhatsappWebhookController {

    private final ApplicationProperties properties;
    private final WhatsappWebhookService service;

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Meta's subscription handshake - echoes the challenge")
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        String expected = properties.meta().webhookVerifyToken();
        boolean ok = "subscribe".equals(mode)
                && expected != null && !expected.isBlank()
                && expected.equals(token);
        if (!ok) {
            log.warn("Rejected a webhook verification attempt (mode={})", mode);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(challenge);
    }

    @PostMapping
    @Operation(summary = "Delivery statuses, replies and template reviews from Meta")
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String body) {
        if (!service.signatureMatches(signature, body)) {
            log.warn("Rejected an unsigned or badly signed webhook payload");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            service.handle(body);
        } catch (RuntimeException ex) {
            // Meta retries a non-200 and disables the subscription if that keeps
            // failing. One malformed event must not cost every future report, so
            // it is logged and swallowed.
            log.error("Failed to handle a WhatsApp webhook payload", ex);
        }
        return ResponseEntity.ok().build();
    }
}
