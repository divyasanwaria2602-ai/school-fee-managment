package com.school;

import jakarta.validation.constraints.NotBlank;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

record CreateSchoolRequest(
    @NotBlank String name,
    String address,
    String phone,
    String email,
    @NotBlank String adminUsername,
    @NotBlank String adminPassword) {}

record CreateUserRequest(@NotBlank String username, @NotBlank String password) {}

record UserResponse(Long id, String username, Role role, boolean active, Long schoolId, String schoolName) {}

@Service
class UserManagementService {
  private final UserRepository users;
  private final SchoolRepository schools;
  private final PasswordEncoder encoder;
  private final FeeTypeRepository feeTypes;
  private final ClassRepository classes;
  private final AuthorizationService authorization;

  UserManagementService(
      UserRepository users,
      SchoolRepository schools,
      PasswordEncoder encoder,
      FeeTypeRepository feeTypes,
      ClassRepository classes,
      AuthorizationService authorization) {
    this.users = users;
    this.schools = schools;
    this.encoder = encoder;
    this.feeTypes = feeTypes;
    this.classes = classes;
    this.authorization = authorization;
  }

  @Transactional
  School createSchool(Authentication auth, CreateSchoolRequest q) {
    AppUser root = authorization.actor(auth);
    authorization.requireRoot(root);

    if (users.findByUsername(q.adminUsername()).isPresent()) {
      throw new IllegalArgumentException("Username already exists");
    }

    School school = new School();
    school.name = q.name().trim();
    school.address = q.address();
    school.phone = q.phone();
    school.email = q.email();
    school.active = true;
    school = schools.save(school);

    AppUser admin = new AppUser();
    admin.username = q.adminUsername().trim();
    admin.passwordHash = encoder.encode(q.adminPassword());
    admin.role = Role.SCHOOL_ADMIN;
    admin.school = school;
    admin.active = true;
    users.save(admin);

    seedFeeTypes(school);
    seedDefaultClasses(school);
    return school;
  }

  @Transactional
  UserResponse createSchoolUser(Authentication auth, CreateUserRequest q) {
    AppUser admin = authorization.actor(auth);
    authorization.requireSchoolAdmin(admin);

    if (users.findByUsername(q.username()).isPresent()) {
      throw new IllegalArgumentException("Username already exists");
    }

    AppUser user = new AppUser();
    user.username = q.username().trim();
    user.passwordHash = encoder.encode(q.password());
    user.role = Role.SCHOOL_USER;
    user.school = admin.school;
    user.active = true;
    users.save(user);
    return view(user);
  }

  List<UserResponse> listSchoolUsers(Authentication auth) {
    AppUser admin = authorization.actor(auth);
    authorization.requireSchoolAdmin(admin);
    return users.findBySchoolIdAndRoleOrderByUsername(admin.school.id, Role.SCHOOL_USER).stream()
        .map(this::view)
        .toList();
  }

  @Transactional
  UserResponse setSchoolUserActive(Authentication auth, Long id, boolean active) {
    AppUser admin = authorization.actor(auth);
    authorization.requireSchoolAdmin(admin);

    AppUser user =
        users.findById(id).orElseThrow(() -> new NoSuchElementException("User not found"));
    if (user.role != Role.SCHOOL_USER) {
      throw new AccessDeniedException("Only school users may be managed");
    }
    authorization.requireSameSchool(admin, user.school);

    user.active = active;
    return view(users.save(user));
  }

  UserResponse view(AppUser user) {
    return new UserResponse(user.id, user.username, user.role, user.active, user.school == null ? null : user.school.id, user.school == null ? null : user.school.name);
  }

  private void seedDefaultClasses(School school) {
    String[] names = {"Nursery", "LKG", "UKG", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"};
    for (String name : names) {
      SchoolClass c = new SchoolClass();
      c.school = school;
      c.name = name;
      c.section = "";
      c.active = true;
      classes.save(c);
    }
  }

  private void seedFeeTypes(School school) {
    String[][] seed = {
      {"TUITION", "Tuition Fee"},
      {"INSTITUTE", "Institute Fee"},
      {"VAN", "Van Fee"}
    };
    for (String[] item : seed) {
      FeeType type = new FeeType();
      type.school = school;
      type.code = item[0];
      type.displayName = item[1];
      type.active = true;
      feeTypes.save(type);
    }
  }
}
