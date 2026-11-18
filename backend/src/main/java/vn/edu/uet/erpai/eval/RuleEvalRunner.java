package vn.edu.uet.erpai.eval;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import vn.edu.uet.erpai.rule.definition.RuleDefinition;
import vn.edu.uet.erpai.rule.engine.RuleEngine;
import vn.edu.uet.erpai.rule.engine.RuleResult;

/**
 * Chạy rule engine trên bộ chứng từ có nhãn và tính precision/recall. Chỉ dùng rule SpEL đang bật
 * (rule SQL cần truy vấn ERP thật nên không nằm trong đánh giá offline).
 */
public final class RuleEvalRunner {

  private final RuleEngine engine;

  public RuleEvalRunner(RuleEngine engine) {
    this.engine = engine;
  }

  public RuleEvalReport run(List<EvalCase> cases) {
    var spelRules =
        engine.rules().stream()
            .filter(r -> r.isEnabled() && r.evaluator() == RuleDefinition.Evaluator.SPEL)
            .toList();
    var evaluatedIds = new HashSet<String>();
    spelRules.forEach(r -> evaluatedIds.add(r.ruleId()));
    var tp = new TreeMap<String, int[]>(); // [tp, fp, fn]
    evaluatedIds.forEach(id -> tp.put(id, new int[3]));

    long start = System.nanoTime();
    for (var c : cases) {
      Set<String> actual = new HashSet<>();
      for (RuleResult r : engine.evaluate(c.voucher(), spelRules)) actual.add(r.ruleId());
      for (var id : evaluatedIds) {
        boolean expected = c.expectedRuleIds().contains(id);
        boolean flagged = actual.contains(id);
        var counts = tp.get(id);
        if (expected && flagged) counts[0]++;
        else if (!expected && flagged) counts[1]++;
        else if (expected) counts[2]++;
      }
    }
    long elapsed = Math.max(1, (System.nanoTime() - start) / 1_000_000);

    var perRule = new TreeMap<String, RuleEvalReport.RuleStats>();
    int[] total = new int[3];
    tp.forEach(
        (id, v) -> {
          perRule.put(id, new RuleEvalReport.RuleStats(v[0], v[1], v[2]));
          for (int i = 0; i < 3; i++) total[i] += v[i];
        });
    return new RuleEvalReport(
        cases.size(),
        elapsed,
        cases.size() * 1000.0 / elapsed,
        perRule,
        new RuleEvalReport.RuleStats(total[0], total[1], total[2]));
  }
}
