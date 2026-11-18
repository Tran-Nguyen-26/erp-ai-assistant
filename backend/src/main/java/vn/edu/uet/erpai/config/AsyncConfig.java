package vn.edu.uet.erpai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

  /** Lập lịch đọc tiến độ job để stream SSE (nhẹ, không giữ luồng request). */
  @org.springframework.context.annotation.Bean(destroyMethod = "shutdown")
  public java.util.concurrent.ScheduledExecutorService jobEventsScheduler() {
    return java.util.concurrent.Executors.newScheduledThreadPool(
        2,
        r -> {
          var t = new Thread(r, "job-events");
          t.setDaemon(true);
          return t;
        });
  }

  /** Luồng chạy các lượt chat (SSE), tách khỏi luồng xử lý request. */
  @org.springframework.context.annotation.Bean(destroyMethod = "shutdown")
  public java.util.concurrent.ExecutorService chatExecutor() {
    return java.util.concurrent.Executors.newFixedThreadPool(8);
  }
}
