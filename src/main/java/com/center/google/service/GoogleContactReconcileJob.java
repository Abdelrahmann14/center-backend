package com.center.google.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.center.google.repository.GoogleAccountRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps Google Contacts in step with the roster on a timer, not only on events.
 *
 * <p>The event listeners in {@link GoogleContactSyncService} fire after each
 * save, but they are fire-and-forget: a student written while Google was
 * unreachable loses its event with nobody the wiser, and until now a DELETED
 * student had no handling at all and stayed in Google Contacts forever. This job
 * is the safety net that closes both.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleContactReconcileJob {

    /**
     * How far back to re-check for edits. Comfortably wider than the interval so
     * a pass that runs late, or one whose workspace was skipped, still covers the
     * gap - re-syncing an unchanged student is an idempotent no-op, missing one
     * is a contact that stays wrong.
     */
    private static final Duration WINDOW = Duration.ofMinutes(30);

    private final GoogleAccountRepository accountRepo;
    private final GoogleContactSyncService sync;

    @Scheduled(initialDelayString = "PT90S", fixedDelayString = "PT10M")
    public void reconcile() {
        List<UUID> workspaces = accountRepo.findAll().stream()
                .map(a -> a.getAdminId())
                .distinct()
                .toList();
        if (workspaces.isEmpty()) {
            return;
        }
        OffsetDateTime since = OffsetDateTime.now().minus(WINDOW);
        for (UUID adminId : workspaces) {
            try {
                var result = sync.reconcile(adminId, since);
                if (!result.isEmpty()) {
                    log.info("google reconcile [{}]: {} added, {} refreshed, {} removed",
                            adminId, result.created(), result.refreshed(), result.removed());
                }
            } catch (RuntimeException ex) {
                // One workspace's dead token must not stop the others. The pass
                // for THIS workspace stopped where it failed and will be retried
                // whole on the next tick rather than half-applied now.
                log.warn("google reconcile [{}] stopped: {}", adminId, ex.getMessage());
            }
        }
    }
}
