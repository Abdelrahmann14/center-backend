package com.center.common.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Which browser origins may call this API.
 *
 * <p>Only needed once the frontend is served from a DIFFERENT origin than the
 * API - a Vercel deployment talking to a backend on its own host. Every other
 * way this app runs (the Vite dev proxy, the desktop shell's loopback server, a
 * reverse proxy or Vercel rewrite that forwards /api) is same-origin, and CORS
 * never enters the picture.
 *
 * <p>So the default is EMPTY: no origin is granted cross-origin access unless
 * one is named. An API that answers everybody is not a convenience here - the
 * token lives in the browser, and a permissive origin list is what lets another
 * site spend it.
 */
@Configuration
public class CorsConfig {

    /** Comma-separated origins, e.g. {@code https://center.vercel.app}. */
    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:}") String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (allowedOrigins.isEmpty()) {
            // No mapping registered: the filter finds no configuration, adds no
            // headers, and the browser blocks cross-origin calls as it would
            // with no CORS support at all.
            return source;
        }

        CorsConfiguration config = new CorsConfiguration();
        // Patterns rather than plain origins so a wildcard covers Vercel's
        // per-commit preview domains (https://*-team.vercel.app).
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        // The PDF and export endpoints put the filename in Content-Disposition;
        // a cross-origin reader cannot see a header unless it is exposed.
        config.setExposedHeaders(List.of("Content-Disposition"));
        // Authentication is a bearer token in a header, not a cookie, so
        // credentialed requests are never needed - and allowing them is what
        // makes a wildcard origin dangerous.
        config.setAllowCredentials(false);
        // Cache the preflight for a day, so a token-carrying request does not
        // pay for an extra round trip every time.
        config.setMaxAge(86400L);

        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
