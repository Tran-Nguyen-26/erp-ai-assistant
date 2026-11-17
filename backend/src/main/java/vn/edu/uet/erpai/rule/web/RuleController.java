package vn.edu.uet.erpai.rule.web;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.erpai.common.security.SecurityUtils;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Group;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Severity;
import vn.edu.uet.erpai.rule.service.RuleAdminService;
import vn.edu.uet.erpai.rule.service.RuleResponse;
import vn.edu.uet.erpai.rule.service.RuleSyncService.SyncReport;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

  private final RuleAdminService service;

  public RuleController(RuleAdminService service) {
    this.service = service;
  }

  @GetMapping
  public List<RuleResponse> list(
      @RequestParam(required = false) Group group,
      @RequestParam(required = false) Severity severity,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(required = false) String q) {
    return service.list(group, severity, entityType, enabled, q);
  }

  @GetMapping("/{ruleId}")
  public RuleResponse get(@PathVariable String ruleId) {
    return service.get(ruleId);
  }

  @PatchMapping("/{ruleId}")
  @PreAuthorize("hasRole('ADMIN')")
  public RuleResponse patch(
      @PathVariable String ruleId, @RequestBody RuleAdminService.PatchRequest req) {
    return service.patch(SecurityUtils.currentUser(), ruleId, req);
  }

  @PostMapping("/reload")
  @PreAuthorize("hasRole('ADMIN')")
  public SyncReport reload() {
    return service.reload(SecurityUtils.currentUser());
  }
}
