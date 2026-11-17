package vn.edu.uet.erpai.rule.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
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

  public RuleEngine(RuleLoader loader, SpelRuleEvaluator spel, SqlRuleEvaluator sql) {
    this.rules = loader.loadAll();
    this.spel = spel;
    this.sql = sql;
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
        results.addAll(sql.evaluate(rule, voucher));
        continue;
      }
      if (rule.needs("ACCOUNTS") && data == null) {
        data = reference.get();
      }
      results.addAll(spel.evaluate(rule, voucher, data == null ? RuleReferenceData.EMPTY : data));
    }
    return List.copyOf(results);
  }
}
