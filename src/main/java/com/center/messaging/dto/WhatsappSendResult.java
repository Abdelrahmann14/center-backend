package com.center.messaging.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What one press of a send button did.
 *
 * <p>The button no longer sends. It queues, and a background drain spends the
 * daily allowance Meta grants - so this reports what was <em>accepted for
 * sending</em>, not what has already left. The distinction is the whole point:
 * a lesson of a hundred with forty-five recipients left in the allowance is
 * honestly described as "forty-five now, fifty-five when the window rolls", and
 * the old shape could only have said "sent 45, failed 55", which was a lie in
 * both halves.
 *
 * @param sent        kept for the pages that already read it - equal to
 *                    {@code sendableNow}
 * @param failed      recipients with no usable phone number. A rejection by Meta
 *                    is not counted here because it has not happened yet
 * @param total       recipients this press was responsible for
 * @param queued      rows actually written
 * @param duplicate   skipped: already queued or already sent for this lesson
 * @param sendableNow how many the current allowance pays for immediately
 * @param waiting     the rest, which go by themselves as the window frees them
 * @param nextFreeAt  when the first waiting message becomes payable, null when
 *                    none are waiting. The window is a rolling 24 hours, so this
 *                    is a moving instant and not midnight
 * @param remaining   unique recipients still available to the whole platform
 * @param tier        the ceiling Meta currently grants the whole platform
 * @param blocked     nothing can go at all right now
 */
public record WhatsappSendResult(int sent, int failed, int total, UUID batchId, int queued,
        int duplicate, int sendableNow, int waiting, OffsetDateTime nextFreeAt, long remaining,
        int tier, boolean blocked, String blockedReason) {

    /** The shape callers that never queue anything still use. */
    public static WhatsappSendResult of(int sent, int failed, int total) {
        return new WhatsappSendResult(sent, failed, total, null, 0, 0, sent, 0, null, 0, 0, false,
                null);
    }
}
