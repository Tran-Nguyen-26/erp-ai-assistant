package vn.edu.uet.erpai.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** Datasource DB ứng dụng (PostgreSQL). Là datasource primary: JPA và Liquibase dùng nó. */
@Configuration
public class AppDataSourceConfig {

  @ConfigurationProperties("app.db")
  public record AppDbProperties(String url, String username, String password) {}

  @Bean
  @Primary
  public DataSource dataSource(AppDbProperties props) {
    var ds = new HikariDataSource();
    ds.setJdbcUrl(props.url());
    ds.setUsername(props.username());
    ds.setPassword(props.password());
    ds.setPoolName("app-pool");
    return ds;
  }

  /** Transaction manager mặc định (JPA, DB ứng dụng). ERP có erpTransactionManager riêng. */
  @Bean
  @Primary
  public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
    return new JpaTransactionManager(emf);
  }
}
