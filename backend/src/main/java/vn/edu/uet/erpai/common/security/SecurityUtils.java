package vn.edu.uet.erpai.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.uet.erpai.common.exception.BusinessException;

public final class SecurityUtils {

  private SecurityUtils() {}

  public static CurrentUser currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
      return user;
    }
    throw new BusinessException("AUTH_UNAUTHORIZED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
  }

  /** companyId chỉ được chấp nhận khi trùng công ty của user hoặc user là ADMIN. */
  public static String resolveCompanyId(String requested) {
    var user = currentUser();
    if (requested == null || requested.isBlank() || requested.equals(user.companyId())) {
      return user.companyId();
    }
    if (user.role() == Role.ADMIN) {
      return requested;
    }
    throw new BusinessException(
        "COMPANY_ACCESS_DENIED", "Không được truy cập dữ liệu công ty khác", HttpStatus.FORBIDDEN);
  }
}
