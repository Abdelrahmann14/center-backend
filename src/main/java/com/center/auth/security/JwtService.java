package com.center.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.center.common.config.ApplicationProperties;
import com.center.common.enums.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Issues and verifies the HS256 access tokens. */
@Service
public class JwtService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_ADMIN_ID = "admin_id";

    private final SecretKey key;
    private final int ttlHours;

    public JwtService(ApplicationProperties properties) {
        // Length is enforced by @Size on the property, so this cannot be weak.
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.ttlHours = properties.jwt().ttlHours();
    }

    public String issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole().getValue())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlHours, ChronoUnit.HOURS)));
        // A super admin owns no workspace, so the claim is simply absent for them.
        if (user.getAdminId() != null) {
            builder.claim(CLAIM_ADMIN_ID, user.getAdminId().toString());
        }
        return builder.signWith(key).compact();
    }

    /** @throws io.jsonwebtoken.JwtException when the token is invalid or expired */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        String adminId = claims.get(CLAIM_ADMIN_ID, String.class);
        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                null,
                Role.fromValue(claims.get(CLAIM_ROLE, String.class)),
                adminId != null ? UUID.fromString(adminId) : null);
    }
}
