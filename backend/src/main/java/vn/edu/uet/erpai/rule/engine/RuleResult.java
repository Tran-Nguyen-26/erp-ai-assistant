package vn.edu.uet.erpai.rule.engine;

import java.util.Map;
import vn.edu.uet.erpai.rule.definition.RuleDefinition;

/** Kết quả đánh giá một rule trên một chứng từ (hoặc một dòng). Chỉ tạo ra bởi module rule. */
public record RuleResult(
    String ruleId,
    RuleDefinition.Severity severity,
    String entityRef,
    String message,
    Map<String, Object> evidence,
    String evidenceKey,
    String fingerprint) {}
