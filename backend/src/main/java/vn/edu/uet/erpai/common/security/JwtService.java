package vn.edu.uet.erpai.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import vn.edu.uet.erpai.config.JwtProperties;

@Service
public class JwtService {

  public enum TokenType {
    ACCESS,
    REFRESH
  }

  private final JwtProperties props;
  private final SecretKey key;

  public JwtService(JwtProperties props) {
    this.props = props;
    this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
  }

  public long accessTtlSeconds() {
    return Duration.ofMinutes(props.accessTtlMinutes()).toSeconds();
  }

  public String createAccessToken(CurrentUser user) {
    return create(user, TokenType.ACCESS, Duration.ofMinutes(props.accessTtlMinutes()));
  }

  public String createRefreshToken(CurrentUser user) {
    return create(user, TokenType.REFRESH, Duration.ofDays(props.refreshTtlDays()));
  }

  private String create(CurrentUser user, TokenType type, Duration ttl) {
    var now = Instant.now();
    return Jwts.builder()
        .subject(user.id().toString())
        .claim("username", user.username())
        .claim("role", user.role().name())
        .claim("companyId", user.companyId())
        .claim("typ", type.name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)
        .compact();
  }

  /** Trả về principal nếu token hợp lệ và đúng loại; ngược lại empty. */
  public Optional<CurrentUser> parse(String token, TokenType expected) {
    try {
      Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      if (!expected.name().equals(c.get("typ", String.class))) {
        return Optional.empty();
      }
      return Optional.of(
          new CurrentUser(
              UUID.fromString(c.getSubject()),
              c.get("username", String.class),
              Role.valueOf(c.get("role", String.class)),
              c.get("companyId", String.class)));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
