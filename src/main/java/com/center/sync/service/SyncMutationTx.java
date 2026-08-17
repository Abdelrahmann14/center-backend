package com.center.sync.service;

import java.util.UUID;
import java.util.function.BiFunction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.center.sync.dto.SyncMutation;
import com.center.sync.dto.SyncMutationResult;

import lombok.RequiredArgsConstructor;

/**
 * The transaction boundary around ONE pushed mutation.
 *
 * <p>A push arrives as a batch, but the batch must not share a transaction.
 * Postgres aborts the whole transaction on any constraint violation - every
 * later statement then fails with "current transaction is aborted" - so one bad
 * row used to take the entire batch down with it. Because the client keeps the
 * batch in its outbox and retries it unchanged, that is not a transient failure:
 * the queue stalls permanently on the first poison row. Running each mutation in
 * its own transaction lets exactly that one roll back while the rest commit.
 *
 * <p>This lives in its own bean because Spring's proxy is what starts the new
 * transaction; a {@code this.} call from the service would silently join the
 * caller's transaction instead.
 */
@Component
@RequiredArgsConstructor
public class SyncMutationTx {

    private final JdbcTemplate jdbc;

    /**
     * Look up the idempotency ledger, apply, then record the key.
     *
     * <p>The key is claimed AFTER a successful apply, not before. Claiming first
     * means a mutation refused for a fixable reason (bad payload, failed
     * validation) burns its id: the client corrects it, resends under the same
     * id, and the server answers "duplicate" without ever looking at the new
     * payload - the correction is silently lost. Claiming last keeps a rejection
     * retryable and still collapses a genuine redelivery, because a redelivery
     * of an APPLIED mutation finds the key already present.
     *
     * @param apply receives the mutation and whether this is its first delivery
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncMutationResult run(UUID tenant, SyncMutation m,
            BiFunction<SyncMutation, Boolean, SyncMutationResult> apply) {

        Integer seen = jdbc.queryForObject(
                "SELECT count(*) FROM sync_applied_mutations WHERE admin_id = ? AND mutation_id = ?",
                Integer.class, tenant, m.mutationId());
        boolean firstDelivery = seen == null || seen == 0;

        SyncMutationResult result = apply.apply(m, firstDelivery);

        if (firstDelivery && !"rejected".equals(result.outcome())) {
            jdbc.update(
                    "INSERT INTO sync_applied_mutations (admin_id, mutation_id) "
                            + "VALUES (?, ?) ON CONFLICT DO NOTHING",
                    tenant, m.mutationId());
        }
        return result;
    }
}
