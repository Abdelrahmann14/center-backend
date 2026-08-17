package com.center.google.client;

/**
 * Google refused a call because this project's per-minute quota is spent.
 *
 * <p>Its own type, not a generic failure, because the answer to it is different:
 * nothing is wrong with the request and retrying it later WILL work. The full
 * re-sync catches this to pause and carry on from where it stopped, instead of
 * reporting a run as failed halfway through.
 */
public class GoogleRateLimitException extends RuntimeException {

    private final int retryAfterSeconds;

    public GoogleRateLimitException(int retryAfterSeconds, String message) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    /** How long to wait before the next call. Google's minute quotas reset per minute. */
    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
