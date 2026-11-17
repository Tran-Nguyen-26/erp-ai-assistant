package vn.edu.uet.erpai;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base cho IT chỉ cần PostgreSQL; worker RabbitMQ bị tắt để không kết nối broker ngoài. */
public abstract class AbstractPostgresIT {

  protected static final String JWT_SECRET = TestInfra.JWT_SECRET;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    TestInfra.registerPostgres(r);
    r.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
  }
}
