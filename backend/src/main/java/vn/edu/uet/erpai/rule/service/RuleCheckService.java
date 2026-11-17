package vn.edu.uet.erpai.rule.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.audit.service.ActorType;
import vn.edu.uet.erpai.audit.service.AuditEvent;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Group;
import vn.edu.uet.erpai.rule.engine.RuleEngine;
import vn.edu.uet.erpai.rule.engine.RuleResult;

/** Rà soát tức thời một chứng từ ERP và lưu violation. */
@Service
public class RuleCheckService {

  private final ErpQueryService erp;
  private final RuleEngine engine;
  private final RuleCatalog catalog;
  private final ReferenceDataProvider referenceData;
  private final ViolationService violationService;
  private final ApplicationEventPublisher events;

  public RuleCheckService(
      ErpQueryService erp,
      RuleEngine engine,
      RuleCatalog catalog,
      ReferenceDataProvider referenceData,
      ViolationService violationService,
      ApplicationEventPublisher events) {
    this.erp = erp;
    this.engine = engine;
    this.catalog = catalog;
    this.referenceData = referenceData;
    this.violationService = violationService;
    this.events = events;
  }

  /**
   * Rà soát một chứng từ đã tải và lưu violation (dùng cho job hàng loạt). @return số violation
   * phát hiện
   */
  @Transactional
  public int checkAndStore(
      String companyId,
      ErpVoucher voucher,
      java.util.UUID reviewJobId,
      Set<Group> groups,
      Set<String> ruleIds) {
    var active =
        catalog.all().stream()
            .map(a -> a.definition())
            .filter(d -> d.isEnabled())
            .filter(d -> d.appliesTo(voucher.entityType()))
            .filter(d -> groups == null || groups.isEmpty() || groups.contains(d.group()))
            .filter(d -> ruleIds == null || ruleIds.isEmpty() || ruleIds.contains(d.ruleId()))
            .toList();
    var results =
        engine.evaluate(voucher, active, () -> referenceData.forCompany(voucher.companyId()));
    var ids = active.stream().map(d -> d.ruleId()).collect(Collectors.toSet());
    return violationService.sync(companyId, voucher, ids, results, reviewJobId).size();
  }

  @Transactional
  public CheckResultResponse check(
      CurrentUser user, ErpEntityType type, String entityRef, Set<Group> groups) {
    long start = System.nanoTime();
    ErpVoucher voucher =
        switch (type) {
          case PURCHASE_INVOICE -> erp.getPurchaseInvoice(user.companyId(), entityRef);
          default -> erp.getVoucher(type, user.companyId(), entityRef);
        };
    var active =
        catalog.all().stream()
            .map(a -> a.definition())
            .filter(d -> d.isEnabled())
            .filter(d -> d.appliesTo(type))
            .filter(d -> groups == null || groups.isEmpty() || groups.contains(d.group()))
            .toList();
    var results =
        engine.evaluate(voucher, active, () -> referenceData.forCompany(voucher.companyId()));
    var ids = active.stream().map(d -> d.ruleId()).collect(Collectors.toSet());
    var violations = violationService.sync(user.companyId(), voucher, ids, results, null);

    events.publishEvent(
        new AuditEvent(
            ActorType.USER,
            user.id().toString(),
            user.companyId(),
            "RULE_CHECK_EXECUTED",
            type.name(),
            entityRef,
            Map.of("checkedRules", ids.size(), "violations", violations.size())));
    return new CheckResultResponse(
        voucher.ref(), ids.size(), violations, (System.nanoTime() - start) / 1_000_000);
  }

  /** Kết quả đánh giá trong bộ nhớ: các rule đã chạy và kết quả — KHÔNG lưu violation. */
  public record Evaluation(Set<String> rulesChecked, List<RuleResult> results) {}

  /** Chạy mọi rule SPEL đang bật trên một chứng từ (có thể là bản sao đã áp thay đổi). */
  public Evaluation dryRun(ErpVoucher voucher) {
    return dryRun(voucher, true);
  }

  /**
   * @param includeSql false → bỏ qua rule SQL (dùng khi ERP không khả dụng)
   */
  public Evaluation dryRun(ErpVoucher voucher, boolean includeSql) {
    var active =
        catalog.all().stream()
            .map(a -> a.definition())
            .filter(d -> d.isEnabled())
            .filter(
                d ->
                    includeSql
                        || d.evaluator()
                            == vn.edu.uet.erpai.rule.definition.RuleDefinition.Evaluator.SPEL)
            .filter(d -> d.appliesTo(voucher.entityType()))
            .toList();
    var ids = active.stream().map(d -> d.ruleId()).collect(Collectors.toSet());
    return new Evaluation(
        ids, engine.evaluate(voucher, active, () -> referenceData.forCompany(voucher.companyId())));
  }
}
