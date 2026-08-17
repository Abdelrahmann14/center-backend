package com.center.whatsapp.service;

import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.center.common.tenant.TenantContext;
import com.center.whatsapp.repository.WhatsappNumberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the workspace's WhatsApp memory complete: it discovers student phones
 * nobody has asked about yet, then answers the ones still waiting - whether they
 * are waiting because the device was offline or because Green API was down.
 *
 * <p>The scheduler thread carries no request and therefore no tenant, so the job
 * finds the workspaces with work waiting and re-enters them one at a time; every
 * workspace uses its own Green API instance, and a failure in one must not stop
 * the next.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsappNumberJob {

    /**
     * Green API is rate-limited, so a pass answers only this many per workspace.
     * A roster that has never been checked therefore fills in over a few hours
     * rather than in one burst; the numbers already answered stay answered.
     */
    private static final int ANSWER_PER_WORKSPACE = 60;

    /**
     * Discovery is a single query and a batch insert - no network - so it runs
     * far ahead of the answering and the queue is never the bottleneck.
     */
    private static final int DISCOVER_PER_WORKSPACE = 500;

    /**
     * Longest one sweep may keep starting workspaces.
     *
     * <p>The per-workspace limits bound each workspace, not the pass. Sixty
     * Green API lookups apiece, sequential, against a 20-second read timeout,
     * means one workspace can occupy the sweep for twenty minutes when the third
     * party is unresponsive - and the pass then walks every OTHER workspace at
     * the same cost. On a shared four-thread scheduler that is one thread gone
     * for hours. Whatever a pass does not reach is still pending; the next tick
     * is five minutes away and resumes from the same queue.
     */
    private static final java.time.Duration PASS_BUDGET = java.time.Duration.ofMinutes(4);

    private final WhatsappNumberRepository repository;
    private final WhatsappNumberService numbers;

    @Scheduled(initialDelayString = "PT45S", fixedDelayString = "PT5M")
    public void resolvePending() {
        List<UUID> workspaces =
                repository.workspacesNeedingCheck(WhatsappNumberService.MAX_ATTEMPTS);
        if (workspaces.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + PASS_BUDGET.toNanos();
        int queued = 0;
        int resolved = 0;
        int visited = 0;
        for (UUID adminId : workspaces) {
            if (System.nanoTime() > deadline) {
                log.info("whatsapp sweep budget spent after {} of {} workspace(s); the rest wait for the next pass",
                        visited, workspaces.size());
                break;
            }
            visited++;
            try {
                queued += TenantContext.callAs(adminId,
                        () -> numbers.enqueueUnknownStudentPhones(DISCOVER_PER_WORKSPACE));
                resolved += TenantContext.callAs(adminId,
                        () -> numbers.drainPending(ANSWER_PER_WORKSPACE));
            } catch (RuntimeException ex) {
                log.warn("whatsapp recheck failed for workspace {}: {}", adminId, ex.getMessage());
            }
        }
        if (queued > 0 || resolved > 0) {
            log.info("whatsapp sweep queued {} and answered {} number(s) across {} workspace(s)",
                    queued, resolved, workspaces.size());
        }
    }
}
