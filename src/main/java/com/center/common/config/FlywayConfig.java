package com.center.common.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Repairs the schema history before migrating.
 *
 * <p>Flyway records a checksum for every applied migration and refuses to start
 * when a file no longer matches, which is the right default: an applied migration
 * is history and must not be rewritten. A cosmetic edit to an old file (a comment
 * reworded, for instance) therefore bricks startup even though the SQL that ran is
 * unchanged.
 *
 * <p>{@code repair()} only realigns those recorded checksums and clears failed
 * entries. It never re-runs SQL and never touches application data, so the schema
 * itself is unaffected. Pending migrations then apply as usual.
 *
 * <p><b>dev only.</b> Outside dev the strict check is the safety net that catches
 * a migration being rewritten after it shipped, and that check must stay.
 */
@Configuration
@Profile("dev")
public class FlywayConfig {

    @Bean
    FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
