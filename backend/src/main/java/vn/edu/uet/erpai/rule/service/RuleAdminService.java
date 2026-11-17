package vn.edu.uet.erpai.rule.service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.audit.service.ActorType;
import vn.edu.uet.erpai.audit.service.AuditEvent;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Group;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Severity;
import vn.edu.uet.erpai.rule.domain.RuleRecord;
import vn.edu.uet.erpai.rule.domain.ViolationStatus;
import vn.edu.uet.erpai.rule.repository.RuleRecordRepository;
import vn.edu.uet.erpai.rule.repository.ViolationRepository;

@Service
public class RuleAdminService {

  public record PatchRequest(Boolean enabled, Severity severity, Map<String, Object> params) {}

  private final RuleRecordRepository rules;
  private final ViolationRepository violations;
  private final RuleCatalog catalog;
  private final RuleSyncService syncService;
  private final ApplicationEventPublisher events;

  public RuleAdminService(
      RuleRecordRepository rules,
      ViolationRepository violations,
      RuleCatalog catalog,
      RuleSyncService syncService,
      ApplicationEventPublisher events) {
    this.rules = rules;
    this.violations = violations;
    this.catalog = catalog;
    this.syncService = syncService;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public List<RuleResponse> list(
      Group group, Severity severity, String entityType, Boolean enabled, String q) {
    var counts = openCounts();
    return rules.findAll().stream()
        .filter(r -> group == null || r.getGroup() == group)
        .filter(r -> severity == null || r.getSeverity() == severity)
        .filter(r -> entityType == null || r.getEntityType().equals(entityType))
        .filter(r -> enabled == null || r.isEnabled() == enabled)
        .filter(
            r ->
                q == null
                    || r.getRuleId().toLowerCase().contains(q.toLowerCase())
                    || r.getName().toLowerCase().contains(q.toLowerCase()))
        .sorted(java.util.Comparator.comparing(RuleRecord::getRuleId))
        .map(r -> RuleResponse.from(r, counts.getOrDefault(r.getId(), 0L), false))
        .toList();
  }

  @Transactional(readOnly = true)
  public RuleResponse get(String ruleId) {
    var r = find(ruleId);
    return RuleResponse.from(r, openCounts().getOrDefault(r.getId(), 0L), true);
  }

  @Transactional
  public RuleResponse patch(CurrentUser user, String ruleId, PatchRequest req) {
    var r = find(ruleId);
    var params = new HashMap<>(r.getParams());
    if (req.params() != null) {
      req.params()
          .forEach(
              (k, v) -> {
                var current = r.getParams().get(k);
                if (current == null) {
                  throw invalid("Tham số không tồn tại: " + k);
                }
                if (current instanceof Number != v instanceof Number
                    || current instanceof String != v instanceof String) {
                  throw invalid("Sai kiểu dữ liệu cho tham số: " + k);
                }
                params.put(k, v);
              });
    }
    r.setConfig(
        req.severity() != null ? req.severity() : r.getSeverity(),
        req.enabled() != null ? req.enabled() : r.isEnabled(),
        params);
    r.setVersion(r.getVersion() + 1);
    rules.flush();
    catalog.refresh();
    events.publishEvent(
        new AuditEvent(
            ActorType.USER,
            user.id().toString(),
            user.companyId(),
            "RULE_UPDATED",
            "rule_definition",
            ruleId,
            Map.of(
                "enabled", r.isEnabled(),
                "severity", r.getSeverity().name(),
                "version", r.getVersion())));
    return RuleResponse.from(r, openCounts().getOrDefault(r.getId(), 0L), true);
  }

  public RuleSyncService.SyncReport reload(CurrentUser user) {
    var report = syncService.sync();
    events.publishEvent(
        new AuditEvent(
            ActorType.USER,
            user.id().toString(),
            user.companyId(),
            "RULES_RELOADED",
            "rule_definition",
            null,
            Map.of("added", report.added().size(), "updated", report.updated().size())));
    return report;
  }

  private Map<UUID, Long> openCounts() {
    var out = new HashMap<UUID, Long>();
    for (var row :
        violations.countByRule(
            EnumSet.of(
                ViolationStatus.OPEN, ViolationStatus.EXPLAINED, ViolationStatus.PROPOSED))) {
      out.put((UUID) row[0], (Long) row[1]);
    }
    return out;
  }

  private RuleRecord find(String ruleId) {
    return rules
        .findByRuleId(ruleId)
        .orElseThrow(
            () ->
                new BusinessException(
                    "RULE_NOT_FOUND", "Không tìm thấy rule " + ruleId, HttpStatus.NOT_FOUND));
  }

  private static BusinessException invalid(String msg) {
    return new BusinessException("RULE_PARAM_INVALID", msg);
  }
}
