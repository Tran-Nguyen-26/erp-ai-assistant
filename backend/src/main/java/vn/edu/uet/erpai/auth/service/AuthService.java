package vn.edu.uet.erpai.auth.service;

import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.audit.service.ActorType;
import vn.edu.uet.erpai.audit.service.AuditEvent;
import vn.edu.uet.erpai.auth.domain.AppUser;
import vn.edu.uet.erpai.auth.repository.AppUserRepository;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.common.security.JwtService;
import vn.edu.uet.erpai.common.security.JwtService.TokenType;

@Service
public class AuthService {

  private final AppUserRepository users;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final ApplicationEventPublisher events;

  public AuthService(
      AppUserRepository users,
      PasswordEncoder encoder,
      JwtService jwt,
      ApplicationEventPublisher events) {
    this.users = users;
    this.encoder = encoder;
    this.jwt = jwt;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public LoginResponse login(String username, String password) {
    var user = users.findByUsername(username).orElse(null);
    if (user == null || !encoder.matches(password, user.getPasswordHash())) {
      events.publishEvent(
          new AuditEvent(
              ActorType.SYSTEM,
              null,
              null,
              "LOGIN_FAILED",
              "app_user",
              null,
              Map.of("username", username)));
      throw new BusinessException(
          "AUTH_INVALID_CREDENTIALS", "Sai tên đăng nhập hoặc mật khẩu", HttpStatus.UNAUTHORIZED);
    }
    if (!user.isActive()) {
      throw new BusinessException(
          "AUTH_USER_DISABLED", "Tài khoản đã bị vô hiệu hóa", HttpStatus.FORBIDDEN);
    }
    events.publishEvent(audit(user, "LOGIN_SUCCEEDED"));
    var principal = toPrincipal(user);
    return new LoginResponse(
        jwt.createAccessToken(principal),
        jwt.createRefreshToken(principal),
        "Bearer",
        jwt.accessTtlSeconds(),
        UserResponse.from(user));
  }

  @Transactional(readOnly = true)
  public LoginResponse refresh(String refreshToken) {
    var principal =
        jwt.parse(refreshToken, TokenType.REFRESH)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "AUTH_INVALID_TOKEN",
                        "Refresh token không hợp lệ",
                        HttpStatus.UNAUTHORIZED));
    // Tải lại user để role/trạng thái luôn mới nhất.
    var user =
        users
            .findById(principal.id())
            .filter(AppUser::isActive)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "AUTH_USER_DISABLED", "Tài khoản không khả dụng", HttpStatus.FORBIDDEN));
    var fresh = toPrincipal(user);
    return new LoginResponse(
        jwt.createAccessToken(fresh),
        jwt.createRefreshToken(fresh),
        "Bearer",
        jwt.accessTtlSeconds(),
        null);
  }

  @Transactional(readOnly = true)
  public UserResponse me(CurrentUser current) {
    return users
        .findById(current.id())
        .map(UserResponse::from)
        .orElseThrow(
            () ->
                new BusinessException(
                    "AUTH_UNAUTHORIZED", "Không tìm thấy người dùng", HttpStatus.UNAUTHORIZED));
  }

  @Transactional
  public AppUser createUser(
      String username,
      String rawPassword,
      String fullName,
      vn.edu.uet.erpai.common.security.Role role,
      String companyId) {
    return users.save(
        new AppUser(username, encoder.encode(rawPassword), fullName, role, companyId));
  }

  public boolean existsByUsername(String username) {
    return users.findByUsername(username).isPresent();
  }

  private static CurrentUser toPrincipal(AppUser u) {
    return new CurrentUser(u.getId(), u.getUsername(), u.getRole(), u.getCompanyId());
  }

  private static AuditEvent audit(AppUser u, String action) {
    return new AuditEvent(
        ActorType.USER,
        u.getId().toString(),
        u.getCompanyId(),
        action,
        "app_user",
        u.getId().toString(),
        Map.of());
  }
}
