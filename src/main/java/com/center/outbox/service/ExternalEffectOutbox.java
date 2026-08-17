package com.center.outbox.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.center.outbox.entity.ExternalEffect;
import com.center.outbox.repository.ExternalEffectRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The write side of the external-effect queue: "this has to reach Google /
 * WhatsApp, do it as soon as the line allows".
 *
 * <p>Enqueuing is deliberately its own {@code REQUIRES_NEW} transaction. The
 * queue row is a promise the system makes to itself, and it has to survive
 * whatever the caller's transaction does next - including a rollback that
 * happens precisely because the outside world was unreachable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalEffectOutbox {

    private final ExternalEffectRepository repository;

    /** Queue an effect, collapsing onto the pending one for the same subject. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(UUID adminId, String kind, UUID refId, String payload) {
        if (adminId == null || kind == null) {
            return;
        }
        try {
            ExternalEffect row = refId == null
                    ? null
                    : repository.findByAdminIdAndKindAndRefId(adminId, kind, refId).orElse(null);
            if (row == null) {
                row = new ExternalEffect();
                row.setAdminId(adminId);
                row.setKind(kind);
                row.setRefId(refId);
            }
            // A fresh request supersedes whatever the pending row was waiting on:
            // reset the backoff so the newest state is attempted straight away.
            row.setPayload(payload);
            row.setAttempts(0);
            row.setLastError(null);
            row.setNextAttemptAt(OffsetDateTime.now());
            repository.save(row);
        } catch (DataIntegrityViolationException ex) {
            // Two threads queued the same subject at once; the other one won and
            // the effect is already promised. Nothing to repair.
            log.debug("outbox: {} for {} already queued", kind, refId);
        } catch (RuntimeException ex) {
            // The queue is a safety net, never a reason to fail the real save.
            log.warn("outbox: could not queue {} for {}: {}", kind, refId, ex.getMessage());
        }
    }

    public void enqueue(UUID adminId, String kind, UUID refId) {
        enqueue(adminId, kind, refId, null);
    }
}
