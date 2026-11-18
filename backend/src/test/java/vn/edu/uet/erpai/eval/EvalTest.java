package vn.edu.uet.erpai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.uet.erpai.eval.RankingMetrics.Query;
import vn.edu.uet.erpai.rule.definition.RuleLoader;
import vn.edu.uet.erpai.rule.engine.RuleEngine;
import vn.edu.uet.erpai.rule.engine.SpelRuleEvaluator;
import vn.edu.uet.erpai.rule.engine.SqlRuleEvaluator;

class EvalTest {

  private RuleEngine engine() {
    var spel = new SpelRuleEvaluator();
    return new RuleEngine(
        new RuleLoader(),
        spel,
        new SqlRuleEvaluator(
            org.mockito.Mockito.mock(vn.edu.uet.erpai.erp.service.ErpQueryService.class), spel));
  }

  @Test
  void generatorIsDeterministicAndLabelsMatchInjectedFaults() {
    var a = new SyntheticVoucherFactory(7).generate(200, 0.5);
    var b = new SyntheticVoucherFactory(7).generate(200, 0.5);
    assertThat(a).isEqualTo(b);
    long faulty = a.stream().filter(c -> !c.expectedRuleIds().isEmpty()).count();
    assertThat(faulty).isBetween(60L, 140L);
    assertThat(
            new SyntheticVoucherFactory(7)
                .generate(50, 0).stream().allMatch(c -> c.expectedRuleIds().isEmpty()))
        .isTrue();
  }

  @Test
  void ruleEngineReachesPerfectScoreOnTheSyntheticSetAndIsFast() {
    var report =
        new RuleEvalRunner(engine()).run(new SyntheticVoucherFactory(42).generate(1000, 0.3));

    assertThat(report.vouchers()).isEqualTo(1000);
    assertThat(report.perRule())
        .containsKeys("FLD_TAX_CODE_FORMAT", "FLD_REQUIRED_FIELDS", "FLD_DATE_IN_FUTURE");
    assertThat(report.perRule().get("FLD_TAX_CODE_FORMAT").truePositives()).isPositive();
    assertThat(report.overall().falsePositives()).as("không báo nhầm chứng từ sạch").isZero();
    assertThat(report.overall().falseNegatives()).as("không bỏ sót lỗi đã cài").isZero();
    assertThat(report.overall().f1()).isEqualTo(1.0);
    assertThat(report.elapsedMillis())
        .as("1000 chứng từ trong 30 giây (mục tiêu kiến trúc)")
        .isLessThan(30_000);
  }

  @Test
  void metricsCountMissesAndFalseAlarms() {
    var s = new RuleEvalReport.RuleStats(8, 2, 2);
    assertThat(s.precision()).isEqualTo(0.8);
    assertThat(s.recall()).isEqualTo(0.8);
    assertThat(new RuleEvalReport.RuleStats(0, 0, 0).precision()).isEqualTo(1.0);
  }

  @Test
  void rankingMetricsComputeHitAtKAndMrr() {
    var qs =
        List.of(
            new Query(List.of("a", "b", "c"), Set.of("a")), // hạng 1
            new Query(List.of("x", "b", "c"), Set.of("b")), // hạng 2
            new Query(List.of("x", "y", "c"), Set.of("c", "d")), // hạng 3
            new Query(List.of("x", "y"), Set.of("q"))); // trượt
    assertThat(RankingMetrics.hitAtK(qs, 1)).isEqualTo(0.25);
    assertThat(RankingMetrics.hitAtK(qs, 3)).isEqualTo(0.75);
    assertThat(RankingMetrics.mrr(qs))
        .isCloseTo((1 + 0.5 + 1.0 / 3) / 4, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(RankingMetrics.mrr(List.of())).isZero();
  }

  @Test
  void reportIsWrittenAsMarkdown(@TempDir java.nio.file.Path dir) throws Exception {
    var report = new RuleEvalRunner(engine()).run(new SyntheticVoucherFactory(1).generate(50, 0.3));
    var file = EvalReportWriter.write(report, dir);
    assertThat(Files.readString(file))
        .contains("FLD_TAX_CODE_FORMAT")
        .contains("Precision")
        .contains("TỔNG HỢP");
  }
}
