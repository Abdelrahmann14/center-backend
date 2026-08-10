package com.center.common.tenant;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens a fresh transaction - and therefore a fresh Hibernate session - so that a
 * tenant bound via {@link TenantContext#callAs} just beforehand is actually picked
 * up by the multi-tenancy resolver.
 *
 * <p>Why this exists: Hibernate resolves the tenant ONCE, when a session opens, and
 * {@link TenantIdentifierResolver#validateExistingCurrentSessions()} is {@code false}
 * - the tenant is never re-read for an already-open session. A public flow (student
 * self-registration) has no tenant bound at request entry, so a {@code @Transactional}
 * method there opens its session under {@link TenantIdentifierResolver#NO_TENANT} and
 * every {@code @TenantId} query returns nothing. Binding the tenant AFTER that point
 * is too late.
 *
 * <p>Callers therefore do
 * {@code TenantContext.callAs(adminId, () -> executor.inTenantTx(() -> ...))}: callAs
 * binds the tenant first, then this bean's transactional proxy opens the session with
 * it already in place. It must be a SEPARATE bean - a self-invocation would bypass the
 * transactional proxy and defeat the whole point.
 */
@Component
public class TenantScopedExecutor {

    @Transactional
    public <T> T inTenantTx(Supplier<T> action) {
        return action.get();
    }
}
