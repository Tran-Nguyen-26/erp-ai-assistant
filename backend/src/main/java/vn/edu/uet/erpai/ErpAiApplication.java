package vn.edu.uet.erpai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ErpAiApplication {

  public static void main(String[] args) {
    SpringApplication.run(ErpAiApplication.class, args);
  }
}
