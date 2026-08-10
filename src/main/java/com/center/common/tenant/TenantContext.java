package com.center.common.tenant;
import com.center.auth.security.JwtAuthenticationFilter;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Holds the current request's tenant (the owning Admin's id) for the duration
 * of one request thread.
 *
 * <p>Set once by {@code JwtAuthenticationFilter} from the token (or, for a
 * super admin, from an act-as header) and read by {@link TenantIdentifierResolver}
 * whenever Hibernate needs to scope a query. Always cleared in the filter's
 * {@code finally} so a pooled thread never leaks one tenant into the next
 * request.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID adminId) {
        CURRENT.set(adminId);
    }

    /** The current tenant, or {@code null} when none is bound (e.g. a super admin). */
    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Runs an action scoped to one workspace, then restores whatever was bound
     * before. Used by public flows (student self-registration) that have no
     * token yet but legitimately act inside a specific Admin's workspace - the
     * tenant comes from validated input, never from the caller's headers.
     */
    public static <T> T callAs(UUID adminId, Supplier<T> action) {
        UUID previous = CURRENT.get();
        set(adminId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }
}
