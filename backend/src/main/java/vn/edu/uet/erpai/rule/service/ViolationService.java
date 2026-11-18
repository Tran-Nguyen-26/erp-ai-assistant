package vn.edu.uet.erpai.rule.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Group;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Severity;
import vn.edu.uet.erpai.rule.domain.RuleRecord;
import vn.edu.uet.erpai.rule.domain.Violation;
import vn.edu.uet.erpai.rule.domain.ViolationStatus;
import vn.edu.uet.erpai.rule.engine.RuleResult;
import vn.edu.uet.erpai.rule.repository.RuleRecordRepository;
import vn.edu.uet.erpai.rule.repository.ViolationRepository;

@Service
public class ViolationService {

  /** Trạng thái mà lần rà soát lại có thể tự chuyển thành RESOLVED khi hết lỗi. */
  private static final Set<ViolationStatus> AUTO_RESOLVABLE =
      EnumSet.of(
          ViolationStatus.OPEN, ViolationStatus.EXPLAINED, ViolationStatus.RESOLVED_PENDING_ERP);

  public record Filter(
      List<ViolationStatus> statuses,
      Severity severity,
      String ruleId,
      Group group,
      String entityType,
      String entityRef,
      LocalDate entityDateFrom,
      LocalDate entityDateTo,
      UUID reviewJobId) {}

  public record Summary(
      long total,
      Map<String, Long> bySeverity,
      Map<String, Long> byStatus,
      List<Map<String, Object>> topRules) {}

  private final ViolationRepository violations;
  private final RuleRecordRepository rules;
  private final EntityManager em;

  public ViolationService(
      ViolationRepository violations, RuleRecordRepository rules, EntityManager em) {
    this.violations = violations;
    this.rules = rules;
    this.em = em;
  }

  /**
   * Đồng bộ kết quả rà soát một chứng từ: tạo mới / cập nhật theo fingerprint; violation cũ của các
   * rule đã đánh giá mà không còn xuất hiện được chuyển RESOLVED.
   */
  @Transactional
  public List<ViolationResponse> sync(
      String companyId,
      ErpVoucher voucher,
      Set<String> evaluatedRuleIds,
      List<RuleResult> results,
      UUID reviewJobId) {
    Map<String, RuleRecord> ruleMap =
        rules.findByRuleIdIn(evaluatedRuleIds).stream()
            .collect(Collectors.toMap(RuleRecord::getRuleId, Function.identity()));
    var current = new ArrayList<Violation>();
    var currentFingerprints = new HashSet<String>();
    for (var r : results) {
      var evidence = JsonSafe.evidence(r.evidence());
      var rule = ruleMap.get(r.ruleId());
      var existing = violations.findByFingerprint(r.fingerprint()).orElse(null);
      if (existing == null) {
        existing =
            violations.save(
                new Violation(
                    rule,
                    reviewJobId,
                    companyId,
                    voucher.entityType().name(),
                    voucher.ref(),
                    voucher.voucherNo(),
                    voucher.voucherDate(),
                    r.severity(),
                    r.message(),
                    evidence,
                    r.fingerprint()));
      } else {
        existing.seenAgain(r.message(), evidence, rule.getVersion());
      }
      currentFingerprints.add(r.fingerprint());
      current.add(existing);
    }
    for (var old :
        violations.findByCompanyIdAndEntityTypeAndEntityRefAndStatusIn(
            companyId, voucher.entityType().name(), voucher.ref(), AUTO_RESOLVABLE)) {
      if (evaluatedRuleIds.contains(old.getRule().getRuleId())
          && !currentFingerprints.contains(old.getFingerprint())) {
        old.markResolved();
      }
    }
    return current.stream().map(ViolationResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public Page<ViolationResponse> search(String companyId, Filter f, Pageable pageable) {
    return violations.findAll(spec(companyId, f), pageable).map(ViolationResponse::from);
  }

  /** Danh sách mới nhất theo tên trạng thái (null = mọi trạng thái); dùng cho công cụ agent. */
  @Transactional(readOnly = true)
  public Page<ViolationResponse> latestByStatus(String companyId, String status, int limit) {
    List<ViolationStatus> statuses = null;
    if (status != null) {
      try {
        statuses = List.of(ViolationStatus.valueOf(status.toUpperCase()));
      } catch (IllegalArgumentException e) {
        throw new BusinessException("TOOL_ARG_INVALID", "status không hợp lệ");
      }
    }
    return search(
        companyId,
        new Filter(statuses, null, null, null, null, null, null, null, null),
        org.springframework.data.domain.PageRequest.of(
            0,
            limit,
            org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "detectedAt")));
  }

  @Transactional(readOnly = true)
  public ViolationResponse get(String companyId, UUID id) {
    return ViolationResponse.from(find(companyId, id));
  }

  @Transactional
  public void markExplained(String companyId, UUID id) {
    find(companyId, id).markExplained();
  }

  @Transactional
  public void markProposed(String companyId, UUID id) {
    find(companyId, id).markProposed();
  }

  @Transactional
  public void markResolvedPendingErp(String companyId, UUID id) {
    find(companyId, id).markResolvedPendingErp();
  }

  @Transactional
  public void revertToExplained(String companyId, UUID id) {
    find(companyId, id).revertToExplained();
  }

  @Transactional
  public ViolationResponse dismiss(String companyId, UUID id, UUID userId, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new BusinessException("DISMISS_REASON_REQUIRED", "Phải nhập lý do bỏ qua");
    }
    var v = find(companyId, id);
    v.dismiss(userId, reason.trim());
    return ViolationResponse.from(v);
  }

  @Transactional
  public ViolationResponse reopen(String companyId, UUID id) {
    var v = find(companyId, id);
    if (v.getStatus() != ViolationStatus.DISMISSED && v.getStatus() != ViolationStatus.RESOLVED) {
      throw new BusinessException(
          "VIOLATION_NOT_REOPENABLE",
          "Chỉ mở lại được violation đã bỏ qua hoặc đã xử lý",
          HttpStatus.CONFLICT);
    }
    v.reopen();
    return ViolationResponse.from(v);
  }

  /** Vi phạm đang mở của một chứng từ: số lượng và mức độ nặng nhất. */
  public record OpenSummary(int openCount, String maxSeverity) {}

  private static final Set<ViolationStatus> OPEN_STATUSES =
      EnumSet.of(ViolationStatus.OPEN, ViolationStatus.EXPLAINED, ViolationStatus.PROPOSED);
  private static final String[] SEVERITY_BY_RANK = {"ERROR", "WARNING", "INFO"};

  /**
   * Tổng hợp vi phạm đang mở cho một tập chứng từ (khóa = entityRef); chứng từ không có vi phạm
   * không xuất hiện.
   */
  @Transactional(readOnly = true)
  public Map<String, OpenSummary> openSummaryByRefs(
      String companyId, String entityType, java.util.Collection<String> refs) {
    var out = new LinkedHashMap<String, OpenSummary>();
    if (refs.isEmpty()) {
      return out;
    }
    for (var row : violations.openSummaryByRefs(companyId, entityType, refs, OPEN_STATUSES)) {
      int rank = Math.min(((Number) row[2]).intValue(), SEVERITY_BY_RANK.length - 1);
      out.put(
          (String) row[0], new OpenSummary(((Number) row[1]).intValue(), SEVERITY_BY_RANK[rank]));
    }
    return out;
  }

  /** Khóa các chứng từ đang có vi phạm mở trong khoảng ngày (mới phát hiện trước) và tổng số. */
  public record OpenRefs(List<String> refs, long total) {}

  @Transactional(readOnly = true)
  public OpenRefs openEntityRefs(
      String companyId, String entityType, LocalDate from, LocalDate to, Pageable pageable) {
    return new OpenRefs(
        violations.openEntityRefs(companyId, entityType, OPEN_STATUSES, from, to, pageable),
        violations.countOpenEntityRefs(companyId, entityType, OPEN_STATUSES, from, to));
  }

  @Transactional(readOnly = true)
  public Map<String, Long> severityCountsForJob(UUID reviewJobId) {
    var out = new LinkedHashMap<String, Long>();
    violations
        .countSeverityByJob(reviewJobId)
        .forEach(r -> out.put(String.valueOf(r[0]), (Long) r[1]));
    return out;
  }

  @Transactional(readOnly = true)
  public Summary summary(String companyId, LocalDate from, LocalDate to) {
    var byStatus = group(companyId, "status", from, to);
    var bySeverity = group(companyId, "severity", from, to);
    var total = byStatus.values().stream().mapToLong(Long::longValue).sum();
    var cb = em.getCriteriaBuilder();
    var q = cb.createQuery(Object[].class);
    var root = q.from(Violation.class);
    var ruleJoin = root.join("rule");
    q.multiselect(ruleJoin.get("ruleId"), cb.count(root))
        .where(predicates(cb, root, companyId, from, to).toArray(Predicate[]::new))
        .groupBy(ruleJoin.get("ruleId"))
        .orderBy(cb.desc(cb.count(root)));
    var top =
        em.createQuery(q).setMaxResults(5).getResultList().stream()
            .map(r -> Map.<String, Object>of("ruleId", r[0], "count", r[1]))
            .toList();
    return new Summary(total, bySeverity, byStatus, top);
  }

  private Map<String, Long> group(String companyId, String field, LocalDate from, LocalDate to) {
    var cb = em.getCriteriaBuilder();
    var q = cb.createQuery(Object[].class);
    var root = q.from(Violation.class);
    q.multiselect(root.get(field), cb.count(root))
        .where(predicates(cb, root, companyId, from, to).toArray(Predicate[]::new))
        .groupBy(root.get(field));
    var out = new LinkedHashMap<String, Long>();
    em.createQuery(q).getResultList().forEach(r -> out.put(String.valueOf(r[0]), (Long) r[1]));
    return out;
  }

  private static List<Predicate> predicates(
      jakarta.persistence.criteria.CriteriaBuilder cb,
      jakarta.persistence.criteria.Root<Violation> root,
      String companyId,
      LocalDate from,
      LocalDate to) {
    var p = new ArrayList<Predicate>();
    p.add(cb.equal(root.get("companyId"), companyId));
    if (from != null) {
      p.add(cb.greaterThanOrEqualTo(root.get("entityDate"), from));
    }
    if (to != null) {
      p.add(cb.lessThanOrEqualTo(root.get("entityDate"), to));
    }
    return p;
  }

  private Violation find(String companyId, UUID id) {
    return violations
        .findByIdAndCompanyId(id, companyId)
        .orElseThrow(
            () ->
                new BusinessException(
                    "VIOLATION_NOT_FOUND", "Không tìm thấy violation", HttpStatus.NOT_FOUND));
  }

  private static Specification<Violation> spec(String companyId, Filter f) {
    return (root, query, cb) -> {
      var p = new ArrayList<Predicate>();
      p.add(cb.equal(root.get("companyId"), companyId));
      if (f.statuses() != null && !f.statuses().isEmpty()) {
        p.add(root.get("status").in(f.statuses()));
      }
      if (f.severity() != null) {
        p.add(cb.equal(root.get("severity"), f.severity()));
      }
      if (f.ruleId() != null) {
        p.add(cb.equal(root.get("rule").get("ruleId"), f.ruleId()));
      }
      if (f.group() != null) {
        p.add(cb.equal(root.get("rule").get("group"), f.group()));
      }
      if (f.entityType() != null) {
        p.add(cb.equal(root.get("entityType"), f.entityType()));
      }
      if (f.entityRef() != null) {
        p.add(cb.equal(root.get("entityRef"), f.entityRef()));
      }
      if (f.entityDateFrom() != null) {
        p.add(cb.greaterThanOrEqualTo(root.get("entityDate"), f.entityDateFrom()));
      }
      if (f.entityDateTo() != null) {
        p.add(cb.lessThanOrEqualTo(root.get("entityDate"), f.entityDateTo()));
      }
      if (f.reviewJobId() != null) {
        p.add(cb.equal(root.get("reviewJobId"), f.reviewJobId()));
      }
      return cb.and(p.toArray(Predicate[]::new));
    };
  }
}
