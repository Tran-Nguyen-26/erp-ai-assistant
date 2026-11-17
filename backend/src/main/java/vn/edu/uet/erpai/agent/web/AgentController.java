package vn.edu.uet.erpai.agent.web;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.erpai.agent.workflow.ExplainViolationWorkflow;
import vn.edu.uet.erpai.agent.workflow.ExplanationResponse;
import vn.edu.uet.erpai.common.security.SecurityUtils;

@RestController
@RequestMapping("/api/v1/violations")
public class AgentController {

  private final ExplainViolationWorkflow workflow;

  public AgentController(ExplainViolationWorkflow workflow) {
    this.workflow = workflow;
  }

  /**
   * Chế độ JSON đồng bộ (SSE bổ sung sau). withProposal=true: sinh đề xuất sửa nếu LLM gợi ý và
   * vượt kiểm chứng rule.
   */
  @PostMapping("/{id}/explain")
  public ExplanationResponse explain(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "false") boolean regenerate,
      @RequestParam(defaultValue = "true") boolean withProposal) {
    return workflow.explain(SecurityUtils.currentUser(), id, regenerate, withProposal);
  }
}
