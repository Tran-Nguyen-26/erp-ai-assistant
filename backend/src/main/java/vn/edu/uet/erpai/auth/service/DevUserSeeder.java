package vn.edu.uet.erpai.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.security.Role;

/** Tạo 3 tài khoản mặc định — CHỈ chạy ở profile dev. */
@Component
@Profile("dev")
public class DevUserSeeder implements ApplicationRunner {

  private final AuthService authService;
  private final String companyId;

  public DevUserSeeder(
      AuthService authService, @Value("${app.erp.default-company-id:}") String companyId) {
    this.authService = authService;
    this.companyId = companyId.isBlank() ? "DEMO" : companyId;
  }

  @Override
  public void run(ApplicationArguments args) {
    seed("accountant", "accountant@123", "Kế toán viên", Role.ACCOUNTANT);
    seed("chief", "chief@123", "Kế toán trưởng", Role.CHIEF_ACCOUNTANT);
    seed("admin", "admin@123", "Quản trị viên", Role.ADMIN);
  }

  private void seed(String username, String password, String fullName, Role role) {
    if (!authService.existsByUsername(username)) {
      authService.createUser(username, password, fullName, role, companyId);
    }
  }
}
