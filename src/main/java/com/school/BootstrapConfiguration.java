package com.school;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class BootstrapConfiguration {
  private static final Logger log = LoggerFactory.getLogger(BootstrapConfiguration.class);

  @Bean
  ApplicationRunner bootstrapRoot(
      UserRepository users,
      PasswordEncoder encoder,
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      @Value("${BOOTSTRAP_ROOT_USERNAME:}") String username,
      @Value("${BOOTSTRAP_ROOT_PASSWORD:}") String password) {
    final String effectiveUsername =
        (username == null || username.isBlank())
            ? System.getenv("BOOTSTRAP_ROOT_USERNAME")
            : username;
    final String effectivePassword =
        (password == null || password.isBlank())
            ? System.getenv("BOOTSTRAP_ROOT_PASSWORD")
            : password;

    return args -> {
      if (effectiveUsername == null
          || effectiveUsername.isBlank()
          || effectivePassword == null
          || effectivePassword.isBlank()) {
        log.info(
            "Bootstrap root skipped: BOOTSTRAP_ROOT_USERNAME / BOOTSTRAP_ROOT_PASSWORD not provided");
        return;
      }

      log.info("Bootstrap root: waiting for users table to exist before creating ROOT user");
      // Wait for the users table to exist (DB may be restarting). Poll for up to 30s.
      int attempts = 0;
      boolean usersTableFound = false;
      while (attempts < 30) {
        try {
          Integer found =
              jdbc.queryForObject(
                  "SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='users'",
                  Integer.class);
          if (found != null && found == 1) {
            usersTableFound = true;
            break;
          }
        } catch (Exception e) {
          // ignore and retry
        }
        attempts++;
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }

      if (!usersTableFound) {
        log.warn("Bootstrap root: 'users' table not found after waiting - skipping ROOT creation");
        return;
      }

      try {
        if (users.findByUsername(effectiveUsername).isEmpty()) {
          AppUser root = new AppUser();
          root.username = effectiveUsername;
          root.passwordHash = encoder.encode(effectivePassword);
          root.role = Role.ROOT;
          root.school = null;
          root.active = true;
          users.save(root);
          log.info("Bootstrap root: created ROOT user '{}'", effectiveUsername);
        } else {
          log.info(
              "Bootstrap root: user '{}' already exists, skipping creation", effectiveUsername);
        }
      } catch (Exception ex) {
        // If something still fails, log and continue — don't crash the app during startup.
        log.warn("Bootstrap root skipped due to error: {}", ex.getMessage());
      }
    };
  }
}
