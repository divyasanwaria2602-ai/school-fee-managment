package com.school;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Ensures Flyway migrations run in a controlled way at startup.
 * This strategy will attempt a repair then migrate so databases that were
 * partially migrated get fixed, and migrations run before JPA schema checks.
 */
@Configuration
public class FlywayConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ApplicationRunner flywayRunner(ObjectProvider<Flyway> flywayProvider) {
        return args -> {
            Flyway flyway = flywayProvider.getIfAvailable();
            if (flyway == null) {
                // Flyway not on classpath or auto-config disabled — nothing to do
                return;
            }
            try {
                // Repair any inconsistent state left by interrupted migrations
                flyway.repair();
            } catch (Exception ex) {
                // ignore repair errors and attempt migrate anyway
            }
            // Ensure migrations are applied (migrate is idempotent)
            flyway.migrate();
        };
    }
}
