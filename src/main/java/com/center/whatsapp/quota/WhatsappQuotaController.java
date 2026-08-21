package com.center.whatsapp.quota;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.center.whatsapp.queue.WhatsappSendQueue;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * The number under every WhatsApp send button.
 *
 * <p>Meta caps the whole platform at N unique recipients per rolling 24 hours,
 * and until now nothing in the product knew that - not a screen, not a line of
 * code. A teacher pressing send on a hundred-student lesson had no way of
 * learning that the allowance had run out at student forty-five except by
 * scrolling the log afterwards and counting red rows.
 *
 * <p>So every button reads this and prints what is left beside itself. The
 * figure is deliberately platform-wide rather than per teacher: the allowance
 * genuinely is shared, and a per-workspace number would be a comfortable
 * fiction.
 */
@RestController
@RequestMapping("/api/messaging/whatsapp/quota")
@PreAuthorize("hasAuthority('PERM_NOTIFICATION_SEND')")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Messages")
public class WhatsappQuotaController {

    private final WhatsappQuotaService quota;
    private final WhatsappSendQueue queue;

    /**
     * What is left, what is queued, and when the next recipient frees up.
     *
     * @param lectureId optional: also count what is still owed for one lesson,
     *                  which is what a lesson's own send button shows
     */
    @GetMapping
    @Operation(summary = "Remaining daily WhatsApp allowance for the whole platform")
    public QuotaResponse current(@RequestParam(required = false) UUID lectureId,
            @RequestParam(required = false) String origin) {
        WhatsappQuotaService.Quota q = quota.current();
        long waitingHere = lectureId == null || origin == null
                ? queue.waitingTotal()
                : queue.waitingFor(lectureId, origin);
        return new QuotaResponse(q.tier(), q.tierLabel(), q.used(), q.remaining(), q.margin(),
                q.queued(), waitingHere, q.nextFreeAt(), q.stale(), q.qualityRating(),
                q.numberStatus(), q.refreshedAt(), q.exhausted());
    }

    /**
     * @param tier       the ceiling Meta grants, platform-wide
     * @param used       unique recipients already spent in the rolling window
     * @param remaining  what a send may spend right now
     * @param margin     held back, because this side counts accepted messages
     *                   and Meta counts delivered ones
     * @param queued     messages waiting platform-wide
     * @param waiting    messages waiting for whatever the caller asked about
     * @param nextFreeAt when one more recipient becomes available. The window
     *                   rolls continuously; it does not reset at midnight
     * @param stale      the tier has not been read from Meta recently enough to
     *                   be trusted, so the UI should say so rather than present
     *                   a number it cannot vouch for
     */
    public record QuotaResponse(int tier, String tierLabel, long used, long remaining, int margin,
            long queued, long waiting, OffsetDateTime nextFreeAt, boolean stale,
            String qualityRating, String numberStatus, OffsetDateTime refreshedAt,
            boolean exhausted) {}

    /** How one press is getting on. */
    @GetMapping("/batch/{batchId}")
    @Operation(summary = "Progress of one queued batch")
    public WhatsappSendQueue.Progress batch(@PathVariable UUID batchId) {
        return queue.progress(batchId);
    }

    /** Call off whatever of a batch has not gone yet. */
    @DeleteMapping("/batch/{batchId}")
    @Operation(summary = "Cancel the messages of a batch that have not been sent")
    public CancelResponse cancel(@PathVariable UUID batchId) {
        return new CancelResponse(queue.cancel(batchId));
    }

    public record CancelResponse(int cancelled) {}
}
