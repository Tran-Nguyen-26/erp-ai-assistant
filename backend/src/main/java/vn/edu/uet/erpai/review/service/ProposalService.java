package vn.edu.uet.erpai.review.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.audit.service.ActorType;
import vn.edu.uet.erpai.audit.service.AuditEvent;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.review.domain.Proposal;
import vn.edu.uet.erpai.review.domain.Proposal.Status;
import vn.edu.uet.erpai.review.repository.ProposalRepository;
import vn.edu.uet.erpai.rule.service.RuleCheckService;
import vn.edu.uet.erpai.rule.service.ViolationResponse;
import vn.edu.uet.erpai.rule.service.ViolationService;

/**
 * Tạo, kiểm chứng và duyệt đề xuất sửa. Nguyên tắc #5: áp after_data lên BẢN SAO chứng từ rồi chạy
 * lại rule; không qua kiểm chứng thì không lưu. Nguyên tắc #2: không bao giờ ghi vào ERP.
 */
@Service
public class ProposalService {

  public record ChangeRequest(String path, String to) {}

  private static final int MAX_CHANGES = 10;

  private final ProposalRepository proposals;
  private final ViolationService violations;
  private final RuleCheckService checker;
  private final ErpQueryService erp;
  private final VoucherPatcher patcher;
  private final ErpWriteBackPort writeBack;
  private final ApplicationEventPublisher events;

  public ProposalService(
      ProposalRepository proposals,
      ViolationService violations,
      RuleCheckService checker,
      ErpQueryService erp,
      VoucherPatcher patcher,
      ErpWriteBackPort writeBack,
      ApplicationEventPublisher events) {
    this.proposals = proposals;
    this.violations = violations;
    this.checker = checker;
    this.erp = erp;
    this.patcher = patcher;
    this.writeBack = writeBack;
    this.events = events;
  }

  @Transactional
  public ProposalResponse create(
      CurrentUser user,
      UUID violationId,
      CreatorType creator,
      List<ChangeRequest> changes,
      String rationale,
      List<Map<String, Object>> citations) {
    if (changes == null || changes.isEmpty() || changes.size() > MAX_CHANGES) {
      throw new BusinessException(
          "PROPOSAL_PATH_INVALID", "Cần 1 đến " + MAX_CHANGES + " thay đổi");
    }
    if (rationale == null || rationale.isBlank()) {
      throw new BusinessException("PROPOSAL_RATIONALE_REQUIRED", "Phải nêu lý do đề xuất");
    }
    var violation = violations.get(user.companyId(), violationId);
    if (List.of("DISMISSED", "RESOLVED", "RESOLVED_PENDING_ERP").contains(violation.status())) {
      throw new BusinessException(
          "VIOLATION_NOT_PROPOSABLE",
          "Violation ở trạng thái " + violation.status() + " không thể đề xuất",
          HttpStatus.CONFLICT);
    }
    if (!ErpEntityType.PURCHASE_INVOICE.name().equals(violation.entityType())) {
      throw new BusinessException(
          "RULE_ENTITY_TYPE_UNSUPPORTED", "Chưa hỗ trợ đề xuất cho loại chứng từ này");
    }

    var original = erp.getPurchaseInvoice(user.companyId(), violation.entityRef());
    var patched = original;
    var changeJson = new ArrayList<Map<String, Object>>();
    var before = new LinkedHashMap<String, Object>();
    var after = new LinkedHashMap<String, Object>();
    for (var c : changes) {
      if (c == null || c.path() == null || c.to() == null) {
        throw new BusinessException("PROPOSAL_PATH_INVALID", "Mỗi thay đổi cần path và to");
      }
      var from = patcher.read(patched, c.path());
      patched = patcher.apply(patched, c.path(), c.to());
      var to = patcher.read(patched, c.path()); // giá trị đã chuẩn hóa
      changeJson.add(change(c.path(), from, to));
      before.put(c.path(), from);
      after.put(c.path(), to);
    }

    var verification = verify(violation, checker.dryRun(original), checker.dryRun(patched));
    if (!Boolean.TRUE.equals(verification.get("passed"))) {
      throw new BusinessException(
              "PROPOSAL_VERIFICATION_FAILED", "Đề xuất không vượt qua kiểm chứng bằng rule")
          .with("verification", verification);
    }

    for (var old : proposals.findByViolationIdAndStatus(violationId, Status.PENDING)) {
      old.supersede();
    }
    var saved =
        proposals.save(
            new Proposal(
                violationId,
                user.companyId(),
                violation.entityType(),
                violation.entityRef(),
                before,
                after,
                changeJson,
                rationale.trim(),
                citations == null ? List.of() : citations,
                verification,
                creator,
                user.id()));
    violations.markProposed(user.companyId(), violationId);
    audit(
        user,
        creator == CreatorType.AGENT ? ActorType.AGENT : ActorType.USER,
        "PROPOSAL_CREATED",
        saved.getId(),
        Map.of("violationId", violationId.toString(), "changes", changeJson.size()));
    return ProposalResponse.from(saved, violation);
  }

  private Map<String, Object> verify(
      ViolationResponse violation,
      RuleCheckService.Evaluation before,
      RuleCheckService.Evaluation after) {
    var beforePrints = new HashSet<String>();
    before.results().forEach(r -> beforePrints.add(r.fingerprint()));
    var remaining = new ArrayList<Map<String, Object>>();
    var introduced = new ArrayList<Map<String, Object>>();
    for (var r : after.results()) {
      if (r.fingerprint().equals(violation.fingerprint())) {
        remaining.add(Map.of("ruleId", r.ruleId(), "message", r.message()));
      } else if (!beforePrints.contains(r.fingerprint())
          && r.severity() == vn.edu.uet.erpai.rule.definition.RuleDefinition.Severity.ERROR) {
        introduced.add(Map.of("ruleId", r.ruleId(), "message", r.message()));
      }
    }
    boolean ruleStillActive = after.rulesChecked().contains(violation.ruleId());
    var v = new LinkedHashMap<String, Object>();
    v.put("passed", ruleStillActive && remaining.isEmpty() && introduced.isEmpty());
    v.put("rulesChecked", after.rulesChecked().stream().sorted().toList());
    v.put("remainingViolations", remaining);
    v.put("introducedViolations", introduced);
    if (!ruleStillActive) {
      v.put("note", "Rule " + violation.ruleId() + " không còn hiệu lực nên không thể kiểm chứng");
    }
    return v;
  }

  @Transactional(readOnly = true)
  public Page<ProposalResponse> list(
      String companyId, Status status, String entityRef, UUID violationId, Pageable pageable) {
    Specification<Proposal> spec =
        (root, query, cb) -> {
          var p = new ArrayList<Predicate>();
          p.add(cb.equal(root.get("companyId"), companyId));
          if (status != null) p.add(cb.equal(root.get("status"), status));
          if (entityRef != null) p.add(cb.equal(root.get("entityRef"), entityRef));
          if (violationId != null) p.add(cb.equal(root.get("violationId"), violationId));
          return cb.and(p.toArray(Predicate[]::new));
        };
    return proposals.findAll(spec, pageable).map(p -> ProposalResponse.from(p, null));
  }

  @Transactional(readOnly = true)
  public ProposalResponse get(String companyId, UUID id) {
    var p = find(companyId, id);
    return ProposalResponse.from(p, violationOrNull(companyId, p));
  }

  /** Đề xuất PENDING hiện có của violation (nếu có) — dùng khi trả lại giải thích đã cache. */
  @Transactional(readOnly = true)
  public Optional<ProposalResponse> findPending(String companyId, UUID violationId) {
    return proposals.findByViolationIdAndStatus(violationId, Status.PENDING).stream()
        .filter(p -> p.getCompanyId().equals(companyId))
        .findFirst()
        .map(p -> ProposalResponse.from(p, violationOrNull(companyId, p)));
  }

  @Transactional
  public ProposalResponse approve(CurrentUser user, UUID id, String note) {
    var p = find(user.companyId(), id);
    requirePending(p);
    if (p.getCreatedByType() == CreatorType.USER && user.id().equals(p.getCreatedBy())) {
      throw new BusinessException(
          "PROPOSAL_SELF_APPROVAL", "Người tạo đề xuất không được tự duyệt", HttpStatus.FORBIDDEN);
    }
    p.approve(user.id(), note);
    if (p.getViolationId() != null) {
      violations.markResolvedPendingErp(user.companyId(), p.getViolationId());
    }
    audit(user, ActorType.USER, "PROPOSAL_APPROVED", id, Map.of());
    return ProposalResponse.from(p, violationOrNull(user.companyId(), p));
  }

  @Transactional
  public ProposalResponse reject(CurrentUser user, UUID id, String note) {
    if (note == null || note.isBlank()) {
      throw new BusinessException("REJECT_NOTE_REQUIRED", "Phải nhập lý do từ chối");
    }
    var p = find(user.companyId(), id);
    requirePending(p);
    p.reject(user.id(), note.trim());
    if (p.getViolationId() != null) {
      violations.revertToExplained(user.companyId(), p.getViolationId());
    }
    audit(user, ActorType.USER, "PROPOSAL_REJECTED", id, Map.of());
    return ProposalResponse.from(p, violationOrNull(user.companyId(), p));
  }

  /** Xuất phiếu đề nghị điều chỉnh cho các đề xuất đã duyệt; đánh dấu EXPORTED. */
  @Transactional
  public ErpWriteBackPort.ExportedFile export(CurrentUser user, List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      throw new BusinessException("PROPOSAL_EXPORT_EMPTY", "Chưa chọn đề xuất nào");
    }
    var found = proposals.findByIdInAndCompanyId(ids, user.companyId());
    if (found.size() != new HashSet<>(ids).size()) {
      throw new BusinessException(
          "PROPOSAL_NOT_FOUND", "Có đề xuất không tồn tại", HttpStatus.NOT_FOUND);
    }
    for (var p : found) {
      if (p.getStatus() != Status.APPROVED) {
        throw new BusinessException(
            "PROPOSAL_NOT_APPROVED",
            "Chỉ xuất được đề xuất đã duyệt (" + p.getId() + ")",
            HttpStatus.CONFLICT);
      }
    }
    var file = writeBack.export(found);
    found.forEach(Proposal::markExported);
    audit(user, ActorType.USER, "PROPOSAL_EXPORTED", null, Map.of("count", found.size()));
    return file;
  }

  // ---------- helpers ----------

  private Proposal find(String companyId, UUID id) {
    return proposals
        .findByIdAndCompanyId(id, companyId)
        .orElseThrow(
            () ->
                new BusinessException(
                    "PROPOSAL_NOT_FOUND", "Không tìm thấy đề xuất", HttpStatus.NOT_FOUND));
  }

  private static void requirePending(Proposal p) {
    if (p.getStatus() != Status.PENDING) {
      throw new BusinessException(
          "PROPOSAL_NOT_PENDING", "Đề xuất không ở trạng thái chờ duyệt", HttpStatus.CONFLICT);
    }
  }

  private ViolationResponse violationOrNull(String companyId, Proposal p) {
    if (p.getViolationId() == null) return null;
    try {
      return violations.get(companyId, p.getViolationId());
    } catch (BusinessException e) {
      return null;
    }
  }

  private static Map<String, Object> change(String path, String from, String to) {
    var m = new LinkedHashMap<String, Object>();
    m.put("path", path);
    m.put("from", from);
    m.put("to", to);
    return m;
  }

  private void audit(
      CurrentUser user, ActorType actor, String action, UUID id, Map<String, Object> detail) {
    events.publishEvent(
        new AuditEvent(
            actor,
            user.id().toString(),
            user.companyId(),
            action,
            "proposal",
            id == null ? null : id.toString(),
            detail));
  }
}
