package vn.edu.uet.erpai.agent.tool;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.rule.service.ViolationService;

@Component
public class GetViolationTool implements AgentTool {

  private final ViolationService violations;

  public GetViolationTool(ViolationService violations) {
    this.violations = violations;
  }

  @Override
  public String name() {
    return "get_violation";
  }

  @Override
  public String description() {
    return "Lấy chi tiết một vi phạm đã được phát hiện. Input: violationId (UUID). Output: rule, mức độ, trạng thái, thông điệp, bằng chứng.";
  }

  @Override
  public ToolResult execute(CurrentUser user, Map<String, Object> args) {
    UUID id;
    try {
      id = UUID.fromString(ToolArgs.string(args, "violationId", true));
    } catch (IllegalArgumentException e) {
      throw new BusinessException("TOOL_ARG_INVALID", "violationId phải là UUID");
    }
    var v = violations.get(user.companyId(), id);
    return ToolResult.ok(
        v.ruleId() + " — " + v.status(),
        "Rule "
            + v.ruleId()
            + " ("
            + v.ruleName()
            + ")\nMức độ: "
            + v.severity()
            + "\nTrạng thái: "
            + v.status()
            + "\nChứng từ: "
            + v.entityNo()
            + " ("
            + v.entityRef()
            + ")\nThông điệp: "
            + v.message()
            + "\nBằng chứng: "
            + v.evidence());
  }
}
