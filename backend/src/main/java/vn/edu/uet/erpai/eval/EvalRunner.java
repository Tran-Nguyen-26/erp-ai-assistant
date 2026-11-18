package vn.edu.uet.erpai.eval;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.rule.engine.RuleEngine;

/**
 * Chạy bằng: {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,eval}. Báo cáo →
 * eval/reports/.
 */
@Component
@Profile("eval")
public class EvalRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

  private final RuleEngine engine;

  public EvalRunner(RuleEngine engine) {
    this.engine = engine;
  }

  @Override
  public void run(String... args) throws Exception {
    var cases = new SyntheticVoucherFactory(42).generate(1000, 0.3);
    var report = new RuleEvalRunner(engine).run(cases);
    var file = EvalReportWriter.write(report, Path.of("../eval/reports"));
    log.info(
        "Đánh giá rule: {} chứng từ, {} ms, F1 tổng {} → {}",
        report.vouchers(),
        report.elapsedMillis(),
        String.format("%.3f", report.overall().f1()),
        file);
  }
}
