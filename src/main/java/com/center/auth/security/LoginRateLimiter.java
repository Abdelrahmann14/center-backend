package com.center.auth.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.center.common.config.ApplicationProperties;
import com.center.common.exception.TooManyAttemptsException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caps failed password attempts, so a public API cannot be brute-forced.
 *
 * Two counters, because one alone is easy to walk around: a per-account one
 * stops hammering a single login, and a per-address one stops spraying one
 * common password across many accounts. Either tripping locks the caller out
 * for the configured cooldown.
 *
 * State is in memory. That fits a single instance, which is what this deploys
 * as; running several would need a shared store (Redis) for the counters to be
 * seen by all of them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginRateLimiter {

    /** Above this many tracked keys, expired entries are swept before adding more. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final ApplicationProperties properties;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /** One key's failures inside the current window, and its lock if it tripped. */
    private static final class Counter {
        private int failures;
        private Instant windowStart = Instant.now();
        private Instant lockedUntil;
    }

    /**
     * The caller's address. Behind a platform proxy the socket address is the
     * proxy's, so the forwarded header is preferred - it is only trustworthy
     * because nothing but the platform can reach this app directly.
     */
    public String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).strip();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    /** Throws when either counter for this attempt is currently locked. */
    public void checkAllowed(String identity, String ip) {
        Instant now = Instant.now();
        assertNotLocked(accountKey(identity, ip), now);
        assertNotLocked(ipKey(ip), now);
    }

    /** Counts one failed attempt, locking the key once it passes its limit. */
    public void recordFailure(String identity, String ip) {
        var login = properties.security().login();
        bump(accountKey(identity, ip), login.maxAttempts());
        bump(ipKey(ip), login.perIpMaxAttempts());
    }

    /** A correct password clears the account's record; the address keeps its own. */
    public void recordSuccess(String identity, String ip) {
        counters.remove(accountKey(identity, ip));
    }

    private void assertNotLocked(String key, Instant now) {
        Counter counter = counters.get(key);
        if (counter == null) {
            return;
        }
        synchronized (counter) {
            if (counter.lockedUntil == null || !now.isBefore(counter.lockedUntil)) {
                return;
            }
            long minutes = Math.max(1, Duration.between(now, counter.lockedUntil).toMinutes() + 1);
            throw new TooManyAttemptsException(
                    "محاولات كثيرة خاطئة. حاول مرة أخرى بعد " + minutes + " دقيقة");
        }
    }

    private void bump(String key, int max) {
        var login = properties.security().login();
        Instant now = Instant.now();
        sweepIfCrowded(now);

        Counter counter = counters.computeIfAbsent(key, k -> new Counter());
        synchronized (counter) {
            // A quiet window means the earlier failures no longer count.
            if (Duration.between(counter.windowStart, now).toMinutes() >= login.windowMinutes()) {
                counter.failures = 0;
                counter.windowStart = now;
                counter.lockedUntil = null;
            }
            counter.failures++;
            if (counter.failures >= max) {
                counter.lockedUntil = now.plus(Duration.ofMinutes(login.lockMinutes()));
                log.warn("Login locked for {} after {} failed attempts", key, counter.failures);
            }
        }
    }

    /**
     * Keeps the map from growing without bound when many addresses each fail a
     * little. Only runs once the map is already large, so the normal path stays
     * a single hash lookup.
     */
    private void sweepIfCrowded(Instant now) {
        if (counters.size() < SWEEP_THRESHOLD) {
            return;
        }
        long windowMinutes = properties.security().login().windowMinutes();
        counters.values().removeIf(counter -> {
            synchronized (counter) {
                boolean locked = counter.lockedUntil != null && now.isBefore(counter.lockedUntil);
                boolean fresh = Duration.between(counter.windowStart, now).toMinutes() < windowMinutes;
                return !locked && !fresh;
            }
        });
    }

    private static String accountKey(String identity, String ip) {
        return "acct:" + (identity == null ? "" : identity.strip().toLowerCase()) + "|" + ip;
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }
}
