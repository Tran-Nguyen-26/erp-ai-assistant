package vn.edu.uet.erpai.auth.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.erpai.auth.service.AuthService;
import vn.edu.uet.erpai.auth.service.LoginResponse;
import vn.edu.uet.erpai.auth.service.UserResponse;
import vn.edu.uet.erpai.common.security.SecurityUtils;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest req) {
    return authService.login(req.username(), req.password());
  }

  @PostMapping("/refresh")
  public LoginResponse refresh(@Valid @RequestBody RefreshRequest req) {
    return authService.refresh(req.refreshToken());
  }

  @GetMapping("/me")
  public UserResponse me() {
    return authService.me(SecurityUtils.currentUser());
  }
}
