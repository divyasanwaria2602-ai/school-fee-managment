package com.school;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class BootstrapConfiguration {
  @Bean
  ApplicationRunner bootstrapAdmin(
      UserRepository users,
      SchoolRepository schools,
      FeeTypeRepository feeTypes,
      PasswordEncoder encoder,
      @Value("${BOOTSTRAP_ADMIN_USERNAME:}") String username,
      @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password,
      @Value("${BOOTSTRAP_SCHOOL_NAME:}") String schoolName) {
    return args -> {
      if (!username.isBlank()
          && !password.isBlank()
          && users.findByUsernameAndActiveTrue(username).isEmpty()) {
        School school = new School();
        school.name = schoolName.isBlank() ? "My School" : schoolName;
        school = schools.save(school);
        for (String[] seed :
            new String[][] {{"INSTITUTE_FEE", "Institute Fee"}, {"VAN_FEE", "Van Fee"}}) {
          FeeType type = new FeeType();
          type.school = school;
          type.code = seed[0];
          type.displayName = seed[1];
          feeTypes.save(type);
        }
        AppUser admin = new AppUser();
        admin.username = username;
        admin.passwordHash = encoder.encode(password);
        admin.role = Role.ADMIN;
        admin.school = school;
        users.save(admin);
      }
    };
  }
}
