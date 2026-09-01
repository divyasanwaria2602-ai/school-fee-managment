package com.school;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ensures Flyway migrations run in a controlled way at startup. This strategy will attempt a repair
 * then migrate so databases that were partially migrated get fixed, and migrations run before JPA
 * schema checks.
 */
@Configuration
public class FlywayConfig {

  private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public ApplicationRunner flywayRunner(ObjectProvider<Flyway> flywayProvider, JdbcTemplate jdbc) {
    return args -> {
      Flyway flyway = flywayProvider.getIfAvailable();
      boolean createdLocally = false;
      if (flyway == null) {
        // Try to create a Flyway instance from the application's DataSource as a fallback.
        try {
          var ds = jdbc.getDataSource();
          if (ds != null) {
            flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
            createdLocally = true;
            log.info("Flyway bean not found; created local Flyway using application's DataSource.");
          } else {
            log.info("Flyway not available and DataSource is null; skipping migration runner.");
            return;
          }
        } catch (NoClassDefFoundError | Exception e) {
          log.info("Flyway not available; skipping migration runner: {}", e.getMessage());
          return;
        }
      }

      try {
        // Repair any inconsistent state left by interrupted migrations
        flyway.repair();
      } catch (Exception ex) {
        log.warn("Flyway repair failed: {}", ex.getMessage());
        // ignore repair errors and attempt migrate anyway
      }
      // Ensure migrations are applied (migrate is idempotent)
      try {
        var result = flyway.migrate();
        log.info("Flyway migrate completed, migrations applied: {}", result.migrationsExecuted);
      } catch (Exception ex) {
        log.error("Flyway migrate failed: {}", ex.getMessage());
        if (createdLocally) {
          log.error(
              "Local Flyway instance failed to migrate; migrating via plugin may be required.");
        }
        throw ex;
      }

      try {
        MigrationInfoService info = flyway.info();
        MigrationInfo[] applied = info.applied();
        if (applied == null || applied.length == 0) {
          log.info("No applied migrations found after migrate.");
        } else {
          log.info("Applied migrations (most recent first):");
          for (int i = applied.length - 1; i >= 0; i--) {
            MigrationInfo m = applied[i];
            log.info("  {} - {} ({})", m.getVersion(), m.getDescription(), m.getState());
          }
        }
      } catch (Exception ex) {
        log.warn("Could not determine Flyway applied migrations: {}", ex.getMessage());
      }

      // Log whether 'users' table exists to aid bootstrap debugging
      try {
        Integer found =
            jdbc.queryForObject(
                "SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='users'",
                Integer.class);
        if (found != null && found == 1) {
          log.info("Database check: 'users' table exists.");
        } else {
          log.warn("Database check: 'users' table not found.");
        }
      } catch (Exception ex) {
        log.warn("Database check for 'users' table failed: {}", ex.getMessage());
      }
    };
  }
}
