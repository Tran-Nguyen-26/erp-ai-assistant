package vn.edu.uet.erpai.rule.engine;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.rule.definition.RuleDefinition;
import vn.edu.uet.erpai.rule.definition.RuleLoader;

/**
 * API công khai của module rule. Rule là nguồn DUY NHẤT quyết định vi phạm (nguyên tắc #1). Hàm
 * thuần: không gọi LLM, không ghi DB.
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
    var results = new ArrayList<RuleResult>();
    for (var rule : candidates) {
      if (!rule.isEnabled()) {
        continue;
      }
      results.addAll(
          rule.evaluator() == RuleDefinition.Evaluator.SQL
              ? sql.evaluate(rule, voucher)
              : spel.evaluate(rule, voucher));
    }
    return List.copyOf(results);
  }
}
