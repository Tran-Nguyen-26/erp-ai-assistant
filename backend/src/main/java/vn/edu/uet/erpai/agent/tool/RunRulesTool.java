package vn.edu.uet.erpai.agent.tool;

import java.util.Map;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.rule.service.RuleCheckService;

/** Chạy rule trong bộ nhớ (KHÔNG lưu violation). Kết luận đúng/sai do rule quyết định. */
@Component
public class RunRulesTool implements AgentTool {

  private final ErpQueryService erp;
  private final RuleCheckService checker;

  public RunRulesTool(ErpQueryService erp, RuleCheckService checker) {
    this.erp = erp;
    this.checker = checker;
  }

  @Override
  public String name() {
    return "run_rules";
  }

  @Override
  public String description() {
    return "Chạy các rule kiểm tra trên một hóa đơn mua hàng (không lưu). Input: entityRef (GUID)."
        + " Output: danh sách vi phạm (rule, mức độ, thông điệp) hoặc 'không vi phạm'.";
  }

  @Override
  public ToolResult execute(CurrentUser user, Map<String, Object> args) {
    var voucher =
        erp.getPurchaseInvoice(user.companyId(), ToolArgs.string(args, "entityRef", true));
    var eval = checker.dryRun(voucher);
    if (eval.results().isEmpty()) {
      return ToolResult.ok(
          "Đã chạy " + eval.rulesChecked().size() + " rule: không vi phạm",
          "Không phát hiện vi phạm (đã chạy " + eval.rulesChecked().size() + " rule).");
    }
    var sb = new StringBuilder();
    eval.results()
        .forEach(
            r ->
                sb.append("- [")
                    .append(r.severity())
                    .append("] ")
                    .append(r.ruleId())
                    .append(": ")
                    .append(r.message())
                    .append('\n'));
    return ToolResult.ok(eval.results().size() + " vi phạm", sb.toString());
  }
}
