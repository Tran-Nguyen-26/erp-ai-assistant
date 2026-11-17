package vn.edu.uet.erpai.rule.web;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.erpai.audit.service.ActorType;
import vn.edu.uet.erpai.audit.service.AuditEvent;
import vn.edu.uet.erpai.common.security.SecurityUtils;
import vn.edu.uet.erpai.common.web.PageResponse;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Group;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Severity;
import vn.edu.uet.erpai.rule.domain.ViolationStatus;
import vn.edu.uet.erpai.rule.service.ViolationResponse;
import vn.edu.uet.erpai.rule.service.ViolationService;

@RestController
@RequestMapping("/api/v1/violations")
public class ViolationController {

  public record DismissRequest(@NotBlank(message = "Phải nhập lý do") String reason) {}

  private static final Set<String> SORTABLE =
      Set.of("detectedAt", "entityDate", "severity", "status");

  private final ViolationService service;
  private final org.springframework.context.ApplicationEventPublisher events;

  public ViolationController(
      ViolationService service, org.springframework.context.ApplicationEventPublisher events) {
    this.service = service;
    this.events = events;
  }

  @GetMapping
  public PageResponse<ViolationResponse> list(
      @RequestParam(required = false) List<ViolationStatus> status,
      @RequestParam(required = false) Severity severity,
      @RequestParam(required = false) String ruleId,
      @RequestParam(required = false) Group group,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) String entityRef,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate entityDateFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate entityDateTo,
      @RequestParam(required = false) UUID reviewJobId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) List<String> sort) {
    var companyId = SecurityUtils.currentUser().companyId();
    var filter =
        new ViolationService.Filter(
            status,
            severity,
            ruleId,
            group,
            entityType,
            entityRef,
            entityDateFrom,
            entityDateTo,
            reviewJobId);
    var result =
        service.search(
            companyId,
            filter,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort(sort)));
    return new PageResponse<>(
        result.getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @GetMapping("/summary")
  public ViolationService.Summary summary(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate periodFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate periodTo) {
    return service.summary(SecurityUtils.currentUser().companyId(), periodFrom, periodTo);
  }

  @GetMapping("/{id}")
  public ViolationResponse get(@PathVariable UUID id) {
    return service.get(SecurityUtils.currentUser().companyId(), id);
  }

  @PostMapping("/{id}/dismiss")
  public ViolationResponse dismiss(
      @PathVariable UUID id, @RequestBody @jakarta.validation.Valid DismissRequest req) {
    var user = SecurityUtils.currentUser();
    var result = service.dismiss(user.companyId(), id, user.id(), req.reason());
    events.publishEvent(
        new AuditEvent(
            ActorType.USER,
            user.id().toString(),
            user.companyId(),
            "VIOLATION_DISMISSED",
            "violation",
            id.toString(),
            Map.of()));
    return result;
  }

  @PostMapping("/{id}/reopen")
  public ViolationResponse reopen(@PathVariable UUID id) {
    var user = SecurityUtils.currentUser();
    var result = service.reopen(user.companyId(), id);
    events.publishEvent(
        new AuditEvent(
            ActorType.USER,
            user.id().toString(),
            user.companyId(),
            "VIOLATION_REOPENED",
            "violation",
            id.toString(),
            Map.of()));
    return result;
  }

  /** sort=field,dir (whitelist). Mặc định detectedAt giảm dần. */
  private static Sort sort(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return Sort.by(Sort.Direction.DESC, "detectedAt");
    }
    var orders = new java.util.ArrayList<Sort.Order>();
    for (var s : raw) {
      var parts = s.split(",");
      if (SORTABLE.contains(parts[0])) {
        orders.add(
            new Sort.Order(
                parts.length > 1 && parts[1].equalsIgnoreCase("asc")
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC,
                parts[0]));
      }
    }
    return orders.isEmpty() ? Sort.by(Sort.Direction.DESC, "detectedAt") : Sort.by(orders);
  }
}
