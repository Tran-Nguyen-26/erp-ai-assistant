package vn.edu.uet.erpai.audit.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.erpai.audit.service.ActorType;
import vn.edu.uet.erpai.audit.service.AuditQueryService;
import vn.edu.uet.erpai.audit.service.DashboardService;
import vn.edu.uet.erpai.audit.service.FeedbackService;
import vn.edu.uet.erpai.common.security.SecurityUtils;
import vn.edu.uet.erpai.common.web.PageResponse;

@RestController
@RequestMapping("/api/v1")
public class AuditController {

  public record FeedbackRequest(
      @NotNull FeedbackService.TargetType targetType,
      @NotNull UUID targetId,
      int rating,
      String comment) {}

  private final AuditQueryService audit;
  private final FeedbackService feedback;
  private final DashboardService dashboard;

  public AuditController(
      AuditQueryService audit, FeedbackService feedback, DashboardService dashboard) {
    this.audit = audit;
    this.feedback = feedback;
    this.dashboard = dashboard;
  }

  @GetMapping("/audit-logs")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN')")
  public PageResponse<AuditQueryService.AuditLogResponse> logs(
      @RequestParam(required = false) ActorType actorType,
      @RequestParam(required = false) String actorId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) String targetId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return audit.search(
        SecurityUtils.currentUser().companyId(),
        new AuditQueryService.Filter(actorType, actorId, action, targetType, targetId, from, to),
        page,
        size);
  }

  @PostMapping("/feedback")
  public ResponseEntity<Void> feedback(@Valid @RequestBody FeedbackRequest req) {
    var result =
        feedback.submit(
            SecurityUtils.currentUser(),
            req.targetType(),
            req.targetId(),
            req.rating(),
            req.comment());
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).build();
  }

  @GetMapping("/dashboard/stats")
  public Map<String, Object> stats(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate periodFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate periodTo) {
    return dashboard.stats(SecurityUtils.currentUser().companyId(), periodFrom, periodTo);
  }
}
