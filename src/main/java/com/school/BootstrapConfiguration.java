package com.school;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class BootstrapConfiguration {
  @Bean
  ApplicationRunner bootstrapRoot(
      UserRepository users,
      PasswordEncoder encoder,
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      @Value("${BOOTSTRAP_ROOT_USERNAME:}") String username,
      @Value("${BOOTSTRAP_ROOT_PASSWORD:}") String password) {
    return args -> {
      // Wait for the users table to exist (DB may be restarting). Poll for up to 30s.
      int attempts = 0;
      while (attempts < 30) {
        try {
          Integer found = jdbc.queryForObject(
              "SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='users'",
              Integer.class);
          if (found != null && found == 1) {
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

      try {
        if (!username.isBlank() && !password.isBlank() && users.findByUsername(username).isEmpty()) {
          AppUser root = new AppUser();
          root.username = username;
          root.passwordHash = encoder.encode(password);
          root.role = Role.ROOT;
          root.school = null;
          root.active = true;
          users.save(root);
        }
      } catch (Exception ex) {
        // If something still fails, log and continue — don't crash the app during startup.
        System.err.println("Bootstrap root skipped: " + ex.getMessage());
      }
    };
  }
}
