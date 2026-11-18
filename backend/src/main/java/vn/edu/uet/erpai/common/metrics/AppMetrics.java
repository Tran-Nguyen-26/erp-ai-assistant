package vn.edu.uet.erpai.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Đo thời gian các thao tác chính (rule, LLM, truy xuất RAG). Chỉ ghi tên thao tác và nhãn ngắn
 * (ruleId, kết quả) — tuyệt đối không đưa nội dung chứng từ/prompt vào nhãn.
 */
@Component
public class AppMetrics {

  private final MeterRegistry registry;

  public AppMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  /** Bản không xuất ra đâu cả, dùng cho unit test và nơi dựng thủ công. */
  public static AppMetrics noop() {
    return new AppMetrics(new SimpleMeterRegistry());
  }

  public <T> T time(String name, Supplier<T> action, String... tags) {
    var sample = Timer.start(registry);
    String outcome = "ok";
    try {
      return action.get();
    } catch (RuntimeException e) {
      outcome = "error";
      throw e;
    } finally {
      var all = new String[tags.length + 2];
      System.arraycopy(tags, 0, all, 0, tags.length);
      all[tags.length] = "outcome";
      all[tags.length + 1] = outcome;
      sample.stop(registry.timer(name, all));
    }
  }

  public void count(String name, double amount, String... tags) {
    registry.counter(name, tags).increment(amount);
  }
}
