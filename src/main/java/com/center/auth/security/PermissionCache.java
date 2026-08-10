package com.center.auth.security;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * A tiny per-user authority cache. Sessions are indefinite, so authorities are
 * resolved from the DB per request rather than baked into the JWT - this keeps
 * that cheap without letting a permission change go unnoticed for long. A short
 * TTL absorbs bursts; write paths call {@link #evict}/{@link #evictWorkspace} so
 * grant and module-toggle changes take effect on the next request.
 */
@Component
public class PermissionCache {

    private static final long TTL_MS = 60_000;

    private record Entry(List<GrantedAuthority> authorities, UUID adminId, long expiresAt) {}

    private final ConcurrentHashMap<UUID, Entry> byUser = new ConcurrentHashMap<>();

    /** Returns cached authorities or computes, caches, and returns fresh ones. */
    public List<GrantedAuthority> get(UUID userId, UUID adminId, Supplier<List<GrantedAuthority>> loader) {
        long now = System.currentTimeMillis();
        Entry cached = byUser.get(userId);
        if (cached != null && cached.expiresAt() > now) {
            return cached.authorities();
        }
        List<GrantedAuthority> fresh = List.copyOf(loader.get());
        byUser.put(userId, new Entry(fresh, adminId, now + TTL_MS));
        return fresh;
    }

    /** Drop one user's entry (e.g. after their permissions change). */
    public void evict(UUID userId) {
        if (userId != null) {
            byUser.remove(userId);
        }
    }

    /**
     * Drop every principal in a workspace - the admin themself (their own entry is
     * keyed by their id and tagged with their id as adminId) and all their users.
     * Called when the super admin toggles a module for the admin.
     */
    public void evictWorkspace(UUID adminId) {
        if (adminId == null) {
            return;
        }
        byUser.remove(adminId);
        byUser.values().removeIf(e -> adminId.equals(e.adminId()));
    }
}
