package vn.edu.uet.erpai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

  /** Luồng chạy các lượt chat (SSE), tách khỏi luồng xử lý request. */
  @org.springframework.context.annotation.Bean(destroyMethod = "shutdown")
  public java.util.concurrent.ExecutorService chatExecutor() {
    return java.util.concurrent.Executors.newFixedThreadPool(8);
  }
}
