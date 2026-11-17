package vn.edu.uet.erpai.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Datasource ERP (SQL Server) — CHỈ ĐỌC. Không khai báo JPA/Liquibase cho datasource này. Mọi
 * transaction phải dùng {@code @Transactional(readOnly = true, transactionManager =
 * "erpTransactionManager")}.
 */
@Configuration
public class ErpDataSourceConfig {

  @ConfigurationProperties("app.erp.db")
  public record ErpDbProperties(String url, String username, String password) {}

  @Bean
  public DataSource erpDataSource(ErpDbProperties props) {
    var ds = new HikariDataSource();
    ds.setJdbcUrl(props.url());
    ds.setUsername(props.username());
    ds.setPassword(props.password());
    ds.setPoolName("erp-pool");
    ds.setReadOnly(true);
    ds.setMinimumIdle(0);
    ds.setMaximumPoolSize(5);
    // Không làm app chết khi chưa kết nối được ERP (VPN...): lỗi sẽ báo ở health check.
    ds.setInitializationFailTimeout(-1);
    ds.setConnectionTimeout(5_000);
    return ds;
  }

  @Bean
  public NamedParameterJdbcTemplate erpJdbcTemplate(DataSource erpDataSource) {
    return new NamedParameterJdbcTemplate(erpDataSource);
  }

  @Bean
  public PlatformTransactionManager erpTransactionManager(DataSource erpDataSource) {
    var tm = new DataSourceTransactionManager(erpDataSource);
    tm.setEnforceReadOnly(true);
    return tm;
  }
}
