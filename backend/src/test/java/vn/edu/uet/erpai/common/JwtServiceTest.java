package vn.edu.uet.erpai.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.common.security.JwtService;
import vn.edu.uet.erpai.common.security.JwtService.TokenType;
import vn.edu.uet.erpai.common.security.Role;
import vn.edu.uet.erpai.config.JwtProperties;

class JwtServiceTest {

  private static final String SECRET = "x".repeat(64);
  private final JwtService jwt = new JwtService(new JwtProperties(SECRET, 60, 7));
  private final CurrentUser user =
      new CurrentUser(UUID.randomUUID(), "chief", Role.CHIEF_ACCOUNTANT, "C1");

  @Test
  void accessTokenRoundTrips() {
    var parsed = jwt.parse(jwt.createAccessToken(user), TokenType.ACCESS);
    assertThat(parsed).contains(user);
  }

  @Test
  void refreshTokenIsRejectedAsAccessToken() {
    assertThat(jwt.parse(jwt.createRefreshToken(user), TokenType.ACCESS)).isEmpty();
  }

  @Test
  void tamperedOrGarbageTokenIsRejected() {
    assertThat(jwt.parse(jwt.createAccessToken(user) + "x", TokenType.ACCESS)).isEmpty();
    assertThat(jwt.parse("garbage", TokenType.ACCESS)).isEmpty();
  }

  @Test
  void shortSecretFailsFast() {
    assertThatThrownBy(() -> new JwtProperties("short", 60, 7))
        .isInstanceOf(IllegalStateException.class);
  }
}
