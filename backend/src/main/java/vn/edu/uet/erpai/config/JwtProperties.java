package vn.edu.uet.erpai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.jwt")
public record JwtProperties(String secret, int accessTtlMinutes, int refreshTtlDays) {

  public JwtProperties {
    if (secret == null || secret.length() < 64) {
      throw new IllegalStateException(
          "app.security.jwt.secret (JWT_SECRET) phải có tối thiểu 64 ký tự");
    }
  }
}
