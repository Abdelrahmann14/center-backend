package com.center.auth.security;

import java.io.IOException;

import java.util.Collection;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.center.common.enums.Role;
import com.center.common.tenant.TenantContext;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authenticates a Bearer token per request. A bad token is not rejected here -
 * the context is simply left empty and {@link JwtAuthenticationEntryPoint}
 * renders the 401, so the reason survives as a request attribute.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final PermissionResolver permissionResolver;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            authenticate(request, header.substring(PREFIX.length()).trim());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // Never let one request's tenant leak into the next on a pooled thread.
            TenantContext.clear();
        }
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            AuthenticatedUser user = jwtService.parse(token);
            // Role + fine-grained PERM_* authorities, resolved from the DB (cached).
            // If resolution fails (e.g. a transient DB blip) fall back to role-only
            // so identity endpoints keep working - it fails closed on permissions,
            // never open, and recovers on the next request.
            Collection<? extends GrantedAuthority> authorities;
            try {
                authorities = permissionResolver.authorities(user);
            } catch (RuntimeException ex) {
                authorities = user.getAuthorities();
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    user, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            bindTenant(user);
        } catch (ExpiredJwtException ex) {
            request.setAttribute(JwtAuthenticationEntryPoint.REASON, "انتهت صلاحية الجلسة");
        } catch (JwtException | IllegalArgumentException ex) {
            request.setAttribute(JwtAuthenticationEntryPoint.REASON, "جلسة غير صالحة");
        }
    }

    /**
     * Bind the workspace this request may see. A normal user is pinned to their
     * own admin; a super admin owns no workspace and is left unbound (it manages
     * the platform through cross-tenant super-admin endpoints, and can no longer
     * browse into a teacher's workspace).
     */
    private void bindTenant(AuthenticatedUser user) {
        if (user.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        TenantContext.set(user.getAdminId());
    }
}
