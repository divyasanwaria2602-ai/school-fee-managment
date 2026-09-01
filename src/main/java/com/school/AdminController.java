package com.school;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminController {
  private static final Logger log = LoggerFactory.getLogger(AdminController.class);

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JdbcTemplate jdbc;
  private final String bootstrapTokenEnv;

  public AdminController(
      UserRepository users,
      PasswordEncoder encoder,
      JdbcTemplate jdbc,
      @Value("${BOOTSTRAP_ADMIN_TOKEN:}") String bootstrapTokenEnv) {
    this.users = users;
    this.encoder = encoder;
    this.jdbc = jdbc;
    this.bootstrapTokenEnv = bootstrapTokenEnv;
  }

  record CreateRootRequest(String username, String password, String token) {}

  @PostMapping("/create-root")
  public ResponseEntity<Map<String, String>> createRoot(
      @RequestBody(required = false) CreateRootRequest req,
      @RequestHeader(value = "X-BOOTSTRAP-TOKEN", required = false) String headerToken) {
    String username = null;
    String password = null;
    String token = headerToken;

    if (req != null) {
      if (req.username() != null && !req.username().isBlank()) username = req.username().trim();
      if (req.password() != null && !req.password().isBlank()) password = req.password();
      if (req.token() != null && !req.token().isBlank()) token = req.token();
    }

    if (username == null || username.isBlank()) username = System.getenv("BOOTSTRAP_ROOT_USERNAME");
    if (password == null || password.isBlank()) password = System.getenv("BOOTSTRAP_ROOT_PASSWORD");

    if (username == null || username.isBlank() || password == null || password.isBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              Map.of(
                  "message",
                  "Bootstrap username/password not provided (body or BOOTSTRAP_ROOT_ env vars)"));
    }

    // If a bootstrap token is configured in env, require it
    if (bootstrapTokenEnv != null && !bootstrapTokenEnv.isBlank()) {
      if (token == null || !bootstrapTokenEnv.equals(token)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("message", "Invalid or missing bootstrap token"));
      }
    }

    // Check users table exists
    try {
      Integer found =
          jdbc.queryForObject(
              "SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='users'",
              Integer.class);
      if (found == null || found != 1) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", "Database schema not ready: 'users' table not found"));
      }
    } catch (Exception ex) {
      log.warn("create-root: DB check failed: {}", ex.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("message", "Database check failed: " + ex.getMessage()));
    }

    try {
      if (users.findByUsername(username).isPresent()) {
        return ResponseEntity.ok(Map.of("message", "User already exists"));
      }

      AppUser root = new AppUser();
      root.username = username;
      root.passwordHash = encoder.encode(password);
      root.role = Role.ROOT;
      root.school = null;
      root.active = true;
      users.save(root);
      log.info("create-root: created ROOT user '{}'", username);
      return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "ROOT user created"));
    } catch (Exception ex) {
      log.error("create-root failed: {}", ex.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("message", "Failed to create ROOT user: " + ex.getMessage()));
    }
  }
}
