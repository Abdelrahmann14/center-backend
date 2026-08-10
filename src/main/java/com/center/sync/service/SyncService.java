package com.center.sync.service;
import com.center.common.tenant.TenantContext;

import com.center.sync.dto.SyncPullResponse;
import com.center.sync.dto.SyncPushRequest;
import com.center.sync.dto.SyncPushResponse;

/**
 * The offline-sync endpoints (phase 1: students + attendance). All work is
 * scoped to the caller's tenant, read from {@code TenantContext} which the JWT
 * filter binds - the same isolation every other endpoint enforces.
 */
public interface SyncService {

    /** Apply a batch of offline writes idempotently, returning per-mutation outcomes. */
    SyncPushResponse push(SyncPushRequest request);

    /**
     * One page of the tenant's change feed after {@code since} (0 on first run),
     * newest-first-bounded by {@code limit}.
     */
    SyncPullResponse pull(String since, int limit);
}
