package com.center.auth.security;
import com.center.student.entity.Student;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Stateless JWT security. There is no authentication bypass in any profile -
 * every endpoint below the public list requires a valid token.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/login",
            // Student self-registration - students have no account yet.
            "/api/register/**",
            // Liveness and readiness: the platform's probes carry no token.
            "/api/health",
            "/api/health/ready",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Picks up the CorsConfigurationSource bean (see CorsConfig).
                // Registered here so preflight OPTIONS is answered BEFORE the
                // JWT filter - a preflight carries no Authorization header, and
                // rejecting it would break every cross-origin call.
                .cors(Customizer.withDefaults())
                // No cookies or sessions are used, so CSRF has nothing to protect.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Closing a work session must succeed even once the token expires.
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * $2b$ at cost 12 - the exact format every stored hash already uses, so old
     * passwords keep verifying and new ones look identical.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 12);
    }

    /**
     * The role hierarchy: each rank inherits everything below it, so a rule that
     * requires ROLE_USER is also satisfied by an admin or super admin. Spring
     * Security 6 auto-applies this bean to both URL and method authorization.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        // PARENT is a leaf peer of STUDENT: reachable from the admin ranks above
        // (so staff endpoints stay open to them) but it implies nothing itself.
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("SUPER_ADMIN").implies("ADMIN")
                .role("ADMIN").implies("USER")
                .role("USER").implies("STUDENT", "PARENT")
                .build();
    }
}
