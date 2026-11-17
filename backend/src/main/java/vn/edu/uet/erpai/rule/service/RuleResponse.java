package vn.edu.uet.erpai.rule.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import vn.edu.uet.erpai.rule.domain.RuleRecord;

public record RuleResponse(
    String ruleId,
    String name,
    String group,
    String severity,
    String entityType,
    String evaluator,
    Map<String, Object> params,
    List<String> legalRefs,
    String ragQuery,
    boolean enabled,
    int version,
    long openViolationCount,
    OffsetDateTime updatedAt,
    Map<String, Object> definition) {

  public static RuleResponse from(RuleRecord r, long openCount, boolean withDefinition) {
    return new RuleResponse(
        r.getRuleId(),
        r.getName(),
        r.getGroup().name(),
        r.getSeverity().name(),
        r.getEntityType(),
        r.getEvaluator().name(),
        r.getParams(),
        r.getLegalRefs(),
        r.getRagQuery(),
        r.isEnabled(),
        r.getVersion(),
        openCount,
        r.getUpdatedAt(),
        withDefinition ? r.getDefinition() : null);
  }
}
