package vn.edu.uet.erpai.rule.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.erpai.common.security.SecurityUtils;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Group;
import vn.edu.uet.erpai.rule.service.CheckResultResponse;
import vn.edu.uet.erpai.rule.service.RuleCheckService;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

  public record CheckRequest(
      @NotNull ErpEntityType entityType, @NotBlank String entityRef, Set<Group> ruleGroups) {}

  private final RuleCheckService checkService;

  public ReviewController(RuleCheckService checkService) {
    this.checkService = checkService;
  }

  @PostMapping("/check")
  public CheckResultResponse check(@Valid @RequestBody CheckRequest req) {
    return checkService.check(
        SecurityUtils.currentUser(), req.entityType(), req.entityRef(), req.ruleGroups());
  }
}
