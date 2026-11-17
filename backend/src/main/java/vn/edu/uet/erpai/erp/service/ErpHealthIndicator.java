package vn.edu.uet.erpai.erp.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Health "erpDb": chỉ chạy SELECT 1 trên SQL Server. */
@Component("erpDb")
public class ErpHealthIndicator implements HealthIndicator {

  private final NamedParameterJdbcTemplate erpJdbcTemplate;

  public ErpHealthIndicator(NamedParameterJdbcTemplate erpJdbcTemplate) {
    this.erpJdbcTemplate = erpJdbcTemplate;
  }

  @Override
  public Health health() {
    try {
      erpJdbcTemplate.getJdbcTemplate().queryForObject("SELECT 1", Integer.class);
      return Health.up().build();
    } catch (Exception e) {
      return Health.down().withDetail("error", e.getClass().getSimpleName()).build();
    }
  }
}
