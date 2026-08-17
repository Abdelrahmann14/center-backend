package com.center.common.config;
import com.center.registration.entity.Registration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Application settings, bound from server/.env. Validated at startup so a
 * missing or weak secret fails the boot instead of surfacing at first login.
 */
@ConfigurationProperties(prefix = "app")
@Validated
public record ApplicationProperties(
        @NotNull @Valid Jwt jwt,
        @NotNull @Valid GreenApi greenApi,
        @NotNull @Valid Google google,
        @NotNull @Valid Ai ai,
        @NotNull @Valid Registration registration,
        @NotNull @Valid Security security) {

    public record Jwt(
            /** HS256 needs >= 256 bits of key material. */
            @NotNull @Size(min = 32, message = "JWT_SECRET must be at least 32 characters")
            String secret,

            @Positive int ttlHours) {
    }

    /** WhatsApp delivery for student registration codes. Secrets come from .env. */
    public record GreenApi(
            @NotNull String baseUrl,
            String instanceId,
            String apiToken,
            /** When false, codes are logged instead of sent (local testing only). */
            boolean enabled) {

        /** True only when a real send can actually be attempted. */
        public boolean configured() {
            return enabled
                    && instanceId != null && !instanceId.isBlank()
                    && apiToken != null && !apiToken.isBlank();
        }
    }

    /** Google OAuth client for the Contacts (People API) sync. Blank = feature off. */
    public record Google(
            String clientId,
            String clientSecret,
            String redirectUri) {

        /** True only when an OAuth flow can actually be attempted. */
        public boolean configured() {
            return clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }
    }

    /**
     * AI text generation for message variants, via an OpenAI-compatible API
     * (Groq by default). The key is a secret and lives in .env. Blank = feature
     * off, in which case variant generation reports it is not configured.
     */
    public record Ai(
            @NotNull String baseUrl,
            String apiKey,
            @NotNull String model,
            boolean enabled) {

        /** True only when a real generation call can actually be attempted. */
        public boolean configured() {
            return enabled && apiKey != null && !apiKey.isBlank();
        }
    }

    /** Verification-code lifetime and abuse limits for student self-registration. */
    public record Registration(
            @Positive int codeTtlMinutes,
            @Positive int maxAttempts,
            @Positive int maxSendsPerHour) {
    }

    /** Hardening for the endpoints that check a password. */
    public record Security(@NotNull @Valid Login login) {

        public record Login(
                /** Failed tries for one account from one address before it locks. */
                @Positive int maxAttempts,

                /** Failed tries from one address across all accounts before it locks. */
                @Positive int perIpMaxAttempts,

                /** How long failures keep counting toward the limits. */
                @Positive int windowMinutes,

                /** How long a tripped limit stays locked. */
                @Positive int lockMinutes) {
        }
    }
}
