package com.center.common.controller;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Liveness and readiness, kept deliberately separate.
 *
 * <p>{@code /api/health} answers as long as the process is running. It must stay
 * that way: a platform restarts whatever fails its liveness probe, so wiring a
 * database check into it would turn a brief database blip into a restart loop
 * that guarantees the outage it was meant to report.
 *
 * <p>{@code /api/health/ready} is the one that tells the truth about serving
 * traffic - it takes a pooled connection and asks the database a trivial
 * question. Until this existed the only health signal returned {@code ok:true}
 * unconditionally, so the API looked healthy while every request was timing out
 * waiting for a connection. That is precisely the failure nobody was alerted to.
 */
@RestController
@Slf4j
@Tag(name = "Health")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @GetMapping("/api/health")
    @Operation(summary = "Liveness: the process is up. Never touches the database.")
    public Map<String, Boolean> health() {
        return Map.of("ok", true);
    }

    @GetMapping("/api/health/ready")
    @Operation(summary = "Readiness: a pooled connection can reach the database.")
    public ResponseEntity<Map<String, Object>> ready() {
        long started = System.nanoTime();
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("ok", true, "db_ms", elapsedMs(started)));
        } catch (RuntimeException ex) {
            // The message, not the stack: this endpoint is public and is polled
            // constantly, so it must stay cheap to log and must not leak internals.
            log.warn("readiness: database unreachable after {}ms: {}",
                    elapsedMs(started), ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("ok", false, "db_ms", elapsedMs(started)));
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
