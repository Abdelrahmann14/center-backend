package com.center.whatsapp.cloud.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * The only thing between this server and Meta's per-second ceilings.
 *
 * <p>Meta enforces two rates that have nothing to do with each other, and a
 * sender that respects one and not the other still fails:
 *
 * <ul>
 *   <li><b>80 messages per second per number</b>, counting inbound as well as
 *       outbound. Exceeding it returns 130429.</li>
 *   <li><b>One message every six seconds to the same recipient.</b> Exceeding it
 *       returns 131056 - and this one is easy to trip without meaning to,
 *       because three siblings in a lesson share one parent's phone.</li>
 * </ul>
 *
 * <p>There is also a third, imposed rather than published: when Meta answers a
 * request with a usage header near its budget, or with 130429 or 80007, it is
 * telling us to stop for a while. {@link #pauseFor} is where that lands, and it
 * outranks both rates above.
 *
 * <p><b>Deliberately blocking.</b> The callers are already sequential loops on
 * worker threads, so making them wait here is both the simplest correct thing
 * and what keeps log rows in the order the messages actually left. A non-blocking
 * scheduler would buy nothing and would let the ordering drift.
 *
 * <p>The configured rate is far below Meta's 80: the ceiling is not the point.
 * The point is that a run should be paced by something chosen on purpose rather
 * than by whatever round-trip time the network happens to give, and that the
 * pause hook exists at all.
 */
@Component
@Slf4j
public class WhatsappThrottle {

    /** Meta's pair limit: one message per six seconds to the same recipient. */
    private static final long PAIR_GAP_NANOS = Duration.ofSeconds(6).toNanos();

    /** How long a recipient's last-send stamp is worth keeping. */
    private static final long PAIR_RETENTION_NANOS = PAIR_GAP_NANOS * 10;

    private final long intervalNanos;
    private final long burstNanos;

    /** When the next global permit falls due. Guarded by {@code this}. */
    private long nextSlotNanos = System.nanoTime();

    /** Set by a throttle response. Nothing leaves until this instant passes. */
    private volatile long pausedUntilNanos = System.nanoTime();

    private final Map<String, Long> lastSendPerRecipient = new ConcurrentHashMap<>();

    WhatsappThrottle(@Value("${app.meta.max-mps:8}") double mps,
            @Value("${app.meta.burst:16}") int burst) {
        this.intervalNanos = (long) (1_000_000_000d / Math.max(0.1d, mps));
        this.burstNanos = Math.max(1, burst) * this.intervalNanos;
    }

    /**
     * Blocks until this recipient may be sent to.
     *
     * <p>Three gates in order, because they answer different questions: is
     * everyone stopped, has this person been messaged too recently, and is there
     * a global permit free.
     */
    public void acquire(String recipientPhone) {
        parkUntil(pausedUntilNanos);
        parkUntil(pairFloor(recipientPhone));
        parkUntil(nextGlobalSlot());
        if (recipientPhone != null) {
            lastSendPerRecipient.put(recipientPhone, System.nanoTime());
        }
    }

    /**
     * Stop everything for a while - a 130429, an 80007, or a usage header near
     * its budget.
     *
     * <p>Extends an existing pause, never shortens one. Two responses arriving
     * together must not let the shorter of them cancel the longer.
     */
    public void pauseFor(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return;
        }
        long until = System.nanoTime() + duration.toNanos();
        synchronized (this) {
            // Subtraction, not comparison: System.nanoTime() is allowed to wrap,
            // and `until > pausedUntilNanos` is wrong across the wrap point.
            if (until - pausedUntilNanos > 0) {
                pausedUntilNanos = until;
                log.warn("WhatsApp sending paused for {}s", duration.toSeconds());
            }
        }
    }

    /** How long everything is stopped for, zero when it is not. */
    public Duration pausedFor() {
        long remaining = pausedUntilNanos - System.nanoTime();
        return remaining > 0 ? Duration.ofNanos(remaining) : Duration.ZERO;
    }

    private synchronized long nextGlobalSlot() {
        long now = System.nanoTime();
        // Let idle time bank up to `burst` permits, so a run that starts after a
        // quiet hour is not paced as though it had been sending all along.
        long floor = now - burstNanos;
        if (nextSlotNanos - floor < 0) {
            nextSlotNanos = floor;
        }
        long slot = nextSlotNanos - now > 0 ? nextSlotNanos : now;
        nextSlotNanos = slot + intervalNanos;
        return slot;
    }

    private long pairFloor(String phone) {
        Long last = phone == null ? null : lastSendPerRecipient.get(phone);
        return last == null ? System.nanoTime() : last + PAIR_GAP_NANOS;
    }

    private static void parkUntil(long deadlineNanos) {
        long wait;
        while ((wait = deadlineNanos - System.nanoTime()) > 0) {
            LockSupport.parkNanos(wait);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
        }
    }

    /**
     * Keeps the pair map from growing with the roster.
     *
     * <p>Without this the map holds one entry per phone ever messaged - harmless
     * for a term, unbounded over years, and exactly the kind of leak that only
     * shows up on the one server nobody restarts.
     */
    @Scheduled(fixedDelay = 600_000L)
    void prune() {
        long cutoff = System.nanoTime() - PAIR_RETENTION_NANOS;
        lastSendPerRecipient.values().removeIf(stamp -> stamp - cutoff < 0);
    }
}
