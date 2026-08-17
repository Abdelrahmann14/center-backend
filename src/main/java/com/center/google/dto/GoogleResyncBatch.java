package com.center.google.dto;

/**
 * One slice of a manual "check all numbers" run.
 *
 * <p>The roster is walked a few students at a time rather than in one request:
 * a whole roster in one call reaches a proxy timeout long before it finishes -
 * and, more to the point, a single call can only report "done" or "failed",
 * never how far it got. A slice can, which is what lets the screen draw a real
 * progress bar instead of a spinner that means nothing.
 *
 * @param total      students in the roster
 * @param processed  students covered so far, including this slice
 * @param ok         numbers already correct in Google - nothing was written
 * @param updated    numbers found under a different name and corrected
 * @param created    numbers Google did not have and now does
 * @param retryAfter seconds to wait before asking again; > 0 means Google's
 *                   per-minute quota is spent and this slice did NOT run, so the
 *                   caller should pause and repeat the same offset
 * @param done       true when {@code processed} has reached {@code total}
 */
public record GoogleResyncBatch(int total, int processed, int ok, int updated, int created,
        int retryAfter, boolean done) {
}
