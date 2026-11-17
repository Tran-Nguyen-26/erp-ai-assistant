package vn.edu.uet.erpai;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Container dùng chung cho mọi integration test (khởi động một lần cho cả JVM). */
public final class TestInfra {

  public static final String JWT_SECRET = "t".repeat(64);

  static final PostgreSQLContainer<?> PG =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  static {
    PG.start();
  }

  private TestInfra() {}

  public static void registerPostgres(DynamicPropertyRegistry r) {
    r.add("app.security.jwt.secret", () -> JWT_SECRET);
    r.add("app.db.url", PG::getJdbcUrl);
    r.add("app.db.username", PG::getUsername);
    r.add("app.db.password", PG::getPassword);
  }
}
