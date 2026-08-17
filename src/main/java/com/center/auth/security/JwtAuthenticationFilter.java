package com.center.auth.security;

import java.io.IOException;

import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.center.common.enums.Role;
import com.center.common.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates a Bearer token per request. A bad token is not rejected here -
 * the context is simply left empty and {@link JwtAuthenticationEntryPoint}
 * renders the 401, so the reason survives as a request attribute.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final PermissionResolver permissionResolver;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)
                && !authenticate(request, header.substring(PREFIX.length()).trim())) {
            databaseUnreachable(response);
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // Never let one request's tenant leak into the next on a pooled thread.
            TenantContext.clear();
        }
    }

    /**
     * @return false only when the permissions could not be read because the
     *         database is unreachable - the caller answers 503 instead of letting
     *         the request through with an authority set it could not verify.
     */
    private boolean authenticate(HttpServletRequest request, String token) {
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
                // A dead line is not a lost permission. Role-only here would deny
                // every PERM_-guarded endpoint with "this needs teacher rights",
                // which the client reads as a real refusal and so never queues the
                // write - the one case the offline mirror exists for.
                if (databaseDown(ex)) {
                    log.warn("Permissions unreadable, database unreachable: {}", rootMessage(ex));
                    return false;
                }
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
        return true;
    }

    /** The same three failures {@code GlobalExceptionHandler} answers 503 for. */
    private static boolean databaseDown(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof org.springframework.dao.DataAccessResourceFailureException
                    || t instanceof org.springframework.transaction.CannotCreateTransactionException
                    || t instanceof org.springframework.jdbc.CannotGetJdbcConnectionException) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    /**
     * The same ProblemDetail the handler would have produced, written straight
     * out - a filter runs before the dispatcher, so no {@code @ExceptionHandler}
     * can see it.
     */
    private void databaseUnreachable(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "تعذّر الوصول لقاعدة البيانات");
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
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
