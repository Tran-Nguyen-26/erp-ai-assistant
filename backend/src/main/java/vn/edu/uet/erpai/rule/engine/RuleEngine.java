package vn.edu.uet.erpai.rule.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.edu.uet.erpai.common.metrics.AppMetrics;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.rule.definition.RuleDefinition;
import vn.edu.uet.erpai.rule.definition.RuleLoader;

/**
 * API công khai của module rule. Rule là nguồn DUY NHẤT quyết định vi phạm (nguyên tắc #1). Rule
 * SpEL là hàm thuần (không gọi LLM, không ghi DB); rule SQL chỉ đọc ERP.
 */
@Service
public class RuleEngine {

  private final List<RuleDefinition> rules;
  private final SpelRuleEvaluator spel;
  private final SqlRuleEvaluator sql;

  private final AppMetrics metrics;

  public RuleEngine(RuleLoader loader, SpelRuleEvaluator spel, SqlRuleEvaluator sql) {
    this(loader, spel, sql, AppMetrics.noop());
  }

  @Autowired
  public RuleEngine(
      RuleLoader loader, SpelRuleEvaluator spel, SqlRuleEvaluator sql, AppMetrics metrics) {
    this.rules = loader.loadAll();
    this.spel = spel;
    this.sql = sql;
    this.metrics = metrics;
  }

  public List<RuleDefinition> rules() {
    return rules;
  }

  public List<RuleResult> evaluate(ErpVoucher voucher) {
    return evaluate(voucher, rules);
  }

  public List<RuleResult> evaluate(ErpVoucher voucher, List<RuleDefinition> candidates) {
    return evaluate(voucher, candidates, () -> RuleReferenceData.EMPTY);
  }

  /**
   * @param reference nạp dữ liệu tham chiếu của công ty; chỉ được gọi (một lần) nếu có rule cần đến
   */
  public List<RuleResult> evaluate(
      ErpVoucher voucher, List<RuleDefinition> candidates, Supplier<RuleReferenceData> reference) {
    var results = new ArrayList<RuleResult>();
    RuleReferenceData data = null;
    for (var rule : candidates) {
      if (!rule.isEnabled() || !rule.appliesTo(voucher.entityType())) {
        continue;
      }
      if (rule.evaluator() == RuleDefinition.Evaluator.SQL) {
        results.addAll(
            metrics.time(
                "rule.evaluation", () -> sql.evaluate(rule, voucher), "rule", rule.ruleId()));
        continue;
      }
      if (rule.needs("ACCOUNTS") && data == null) {
        data = reference.get();
      }
      var refData = data == null ? RuleReferenceData.EMPTY : data;
      results.addAll(
          metrics.time(
              "rule.evaluation",
              () -> spel.evaluate(rule, voucher, refData),
              "rule",
              rule.ruleId()));
    }
    return List.copyOf(results);
  }
}
