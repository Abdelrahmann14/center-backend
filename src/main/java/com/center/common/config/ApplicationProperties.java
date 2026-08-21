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
        @NotNull @Valid Meta meta,
        @NotNull @Valid NumberCheck numberCheck,
        @NotNull @Valid Google google,
        @NotNull @Valid Registration registration,
        @NotNull @Valid Security security) {

    public record Jwt(
            /** HS256 needs >= 256 bits of key material. */
            @NotNull @Size(min = 32, message = "JWT_SECRET must be at least 32 characters")
            String secret,

            @Positive int ttlHours) {
    }

    /**
     * WhatsApp Cloud API, hosted by Meta - the only way this system sends. One set
     * of credentials for the whole platform: Meta authenticates the BUSINESS, and
     * each number is addressed by its own {@code phone_number_id} under the same
     * WhatsApp Business Account. That is why these live in the environment rather
     * than on the number's own row.
     */
    public record Meta(
            /** Graph API version, e.g. {@code v26.0}. Pinned, never "latest". */
            @NotNull String apiVersion,

            /** The WhatsApp Business Account every provisioned number belongs to. */
            String wabaId,

            /** The Meta app the token and the webhook belong to. */
            String appId,

            /** Signs the webhook payloads; used to verify they really came from Meta. */
            String appSecret,

            /** Permanent system-user token. Full send + management rights - a secret. */
            String accessToken,

            /** Echoed back on the webhook's GET handshake to prove the URL is ours. */
            String webhookVerifyToken,

            /**
             * What Meta charges per message, by template category, in US dollars.
             *
             * <p>Configuration rather than constants because Meta republishes its
             * rate card per country several times a year and the numbers differ by
             * destination. Everything computed from them is labelled an estimate in
             * the UI - the authoritative figure is the one on the Meta invoice, and
             * a dashboard that pretended otherwise would be lying about money.
             */
            @NotNull Rates rates,

            boolean enabled) {

        /** True only when a real Graph call can actually be attempted. */
        public boolean configured() {
            return enabled
                    && accessToken != null && !accessToken.isBlank()
                    && wabaId != null && !wabaId.isBlank();
        }

        /** {@code https://graph.facebook.com/v26.0} - no trailing slash. */
        public String graphBase() {
            return "https://graph.facebook.com/" + apiVersion;
        }
    }

    /**
     * The one thing the official API cannot do: answer whether a phone number is
     * on WhatsApp at all.
     *
     * <p>Cloud API has no endpoint for it and Meta will not add one - it would be
     * a free enumeration oracle over every phone number alive. The on-premises
     * {@code /v1/contacts} that used to answer it died with the on-premises API
     * in October 2025. So the check - and ONLY the check - goes through Green
     * API, which runs a real WhatsApp client and can ask.
     *
     * <p>Nothing is ever SENT through this. Green carries no message, no card, no
     * template; it is asked a yes/no question about a number and nothing else.
     * That is the whole reason it is acceptable to have back.
     *
     * <p>One instance for the entire platform, not one per teacher. A number
     * either exists on WhatsApp or it does not - the answer is a property of the
     * number, identical for everyone asking, so making each teacher pay for and
     * maintain their own instance would buy nothing. It also means the answer
     * cache is shared: two teachers with the same parent on their roster cost one
     * check between them.
     */
    public record NumberCheck(
            /** Green API host, no trailing slash. */
            @NotNull String baseUrl,

            /** The Green instance id ({@code waInstance<ID>} in their URLs). */
            String instanceId,

            /** That instance's API token - a secret, environment only. */
            String token,

            boolean enabled) {

        /** True only when a real check can actually be attempted. */
        public boolean configured() {
            return enabled
                    && instanceId != null && !instanceId.isBlank()
                    && token != null && !token.isBlank();
        }
    }

    /**
     * Meta's per-message price by template category, in US dollars. SERVICE
     * (a reply inside the 24-hour window) is free and defaults to zero.
     */
    public record Rates(
            java.math.BigDecimal marketing,
            java.math.BigDecimal utility,
            java.math.BigDecimal authentication,
            java.math.BigDecimal service) {

        /** The rate for one of Meta's category names; zero for anything unknown. */
        public java.math.BigDecimal forCategory(String category) {
            if (category == null) {
                return java.math.BigDecimal.ZERO;
            }
            return switch (category.toUpperCase(java.util.Locale.ROOT)) {
                case "MARKETING" -> orZero(marketing);
                case "UTILITY" -> orZero(utility);
                case "AUTHENTICATION" -> orZero(authentication);
                case "SERVICE" -> orZero(service);
                default -> java.math.BigDecimal.ZERO;
            };
        }

        private static java.math.BigDecimal orZero(java.math.BigDecimal value) {
            return value == null ? java.math.BigDecimal.ZERO : value;
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
