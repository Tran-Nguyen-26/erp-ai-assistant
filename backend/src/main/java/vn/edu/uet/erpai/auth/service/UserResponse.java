package vn.edu.uet.erpai.auth.service;

import java.util.UUID;
import vn.edu.uet.erpai.auth.domain.AppUser;
import vn.edu.uet.erpai.common.security.Role;

public record UserResponse(UUID id, String username, String fullName, Role role, String companyId) {

  public static UserResponse from(AppUser u) {
    return new UserResponse(
        u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.getCompanyId());
  }
}
