package com.center.common.tenant;

import java.util.UUID;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Feeds Hibernate's discriminator multi-tenancy: for every {@code @TenantId}
 * entity, Hibernate calls {@link #resolveCurrentTenantIdentifier()} to learn
 * which Admin the current request may see.
 *
 * <p>When no tenant is bound (a super admin who is not acting-as, or a
 * pre-authentication code path) it returns {@link #NO_TENANT} - a sentinel that
 * matches no real row, so such a request reads an empty workspace rather than
 * leaking one. Super admins reach real data by acting-as a specific Admin (which
 * binds a real tenant) or through native aggregate queries that bypass the
 * discriminator entirely.
 */
@Component
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    /** No real Admin has the all-zero id, so it can never match a row. */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID tenant = TenantContext.get();
        return tenant != null ? tenant : NO_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // The tenant is fixed for a request's lifetime; sessions are never reused
        // across tenants, so there is nothing to validate.
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        // Register this resolver so @TenantId entities are actually filtered.
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
