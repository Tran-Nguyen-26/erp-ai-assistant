package vn.edu.uet.erpai.agent.tool;

import java.util.Map;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.rule.service.ViolationService;

@Component
public class ListViolationsTool implements AgentTool {

  private final ViolationService violations;

  public ListViolationsTool(ViolationService violations) {
    this.violations = violations;
  }

  @Override
  public String name() {
    return "list_violations";
  }

  @Override
  public String description() {
    return "Liệt kê vi phạm. Input: status (tùy chọn: OPEN, EXPLAINED, PROPOSED, RESOLVED, DISMISSED), limit (1-50, mặc định 20)."
        + " Output: danh sách (id, rule, mức độ, chứng từ, thông điệp).";
  }

  @Override
  public ToolResult execute(CurrentUser user, Map<String, Object> args) {
    var status = ToolArgs.string(args, "status", false);
    int limit = ToolArgs.limit(args, "limit", 20, 50);
    var page = violations.latestByStatus(user.companyId(), status, limit);
    var sb = new StringBuilder("Tổng số: ").append(page.getTotalElements()).append('\n');
    page.getContent()
        .forEach(
            v ->
                sb.append("- ")
                    .append(v.id())
                    .append(" | ")
                    .append(v.ruleId())
                    .append(" | ")
                    .append(v.severity())
                    .append(" | ")
                    .append(v.entityNo())
                    .append(" | ")
                    .append(v.message())
                    .append('\n'));
    return ToolResult.ok(page.getTotalElements() + " vi phạm", sb.toString());
  }
}
