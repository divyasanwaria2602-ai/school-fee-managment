package com.school;

import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Centralizes role and school/tenant authorization decisions. */
@Service
class AuthorizationService {
  private final UserRepository users;

  AuthorizationService(UserRepository users) {
    this.users = users;
  }

  AppUser actor(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AccessDeniedException("Authentication required");
    }
    return users
        .findByUsernameAndActiveTrue(authentication.getName())
        .orElseThrow(() -> new AccessDeniedException("Active user required"));
  }

  void requireRoot(AppUser user) {
    if (user.role != Role.ROOT) {
      throw new AccessDeniedException("Root administrator required");
    }
  }

  void requireSchoolAdmin(AppUser user) {
    if (user.role != Role.SCHOOL_ADMIN) {
      throw new AccessDeniedException("School administrator required");
    }
    requireAssignedSchool(user);
  }

  void requireSchoolStaff(AppUser user) {
    if (user.role != Role.SCHOOL_ADMIN && user.role != Role.SCHOOL_USER) {
      throw new AccessDeniedException("School user required");
    }
    requireAssignedSchool(user);
  }

  /**
   * Returns the only school a school user may access. Root must explicitly supply a school when an
   * internal root-level operation needs a school scope.
   */
  Long schoolId(AppUser user, Long requestedSchoolId) {
    if (user.role == Role.ROOT) {
      if (requestedSchoolId == null) {
        throw new IllegalArgumentException("schoolId is required for root administrator");
      }
      return requestedSchoolId;
    }

    requireAssignedSchool(user);
    if (requestedSchoolId != null && !Objects.equals(requestedSchoolId, user.school.id)) {
      throw new AccessDeniedException("You cannot access another school");
    }
    return user.school.id;
  }

  void requireSameSchool(AppUser user, School school) {
    requireAssignedSchool(user);
    if (school == null || !Objects.equals(user.school.id, school.id)) {
      throw new AccessDeniedException("School access denied");
    }
    requireActiveSchool(school);
  }

  void requireActiveSchool(School school) {
    if (school == null || !school.active) {
      throw new AccessDeniedException("School is inactive");
    }
  }

  private void requireAssignedSchool(AppUser user) {
    if (user.school == null) {
      throw new AccessDeniedException("User is not assigned to a school");
    }
    requireActiveSchool(user.school);
  }
}
