package com.center.common.web;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Refuses a request body larger than the configured cap, before any handler runs.
 *
 * <p>Multipart uploads are already bounded by {@code spring.servlet.multipart},
 * but a JSON body is not bounded by anything: Jackson reads whatever arrives.
 * {@code /api/sync/push} takes an arbitrarily long list of queued writes, so a
 * single client - or a single corrupted outbox - could ask the server to buffer
 * hundreds of megabytes into heap. On a container capped at a few hundred MB
 * that is an out-of-memory kill, not a slow request.
 *
 * <p>Only the declared {@code Content-Length} is checked. A chunked upload has
 * none and passes through; that is deliberate, because reading ahead to measure
 * it would mean buffering the very bytes this is meant to avoid. Every client
 * here (browser fetch, Expo, the desktop shell) sends a length.
 *
 * <p>Ordered ahead of the security filter chain so an oversized body is dropped
 * without spending a token parse or a permission lookup on it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestSizeLimitFilter(@Value("${app.limits.max-request-bytes:5242880}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        long declared = request.getContentLengthLong();
        if (declared > maxBytes) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader(HttpHeaders.CONNECTION, "close");
            response.getWriter().write(
                    "{\"status\":413,\"detail\":\"حجم الطلب أكبر من المسموح\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Multipart uploads have their own limit; re-checking them here would double-cap. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }
}
