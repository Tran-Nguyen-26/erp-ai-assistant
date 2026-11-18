package vn.edu.uet.erpai.agent.tool;

import java.util.Map;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.erp.service.ErpQueryService;

@Component
public class QueryErpTool implements AgentTool {

  private final ErpQueryService erp;

  public QueryErpTool(ErpQueryService erp) {
    this.erp = erp;
  }

  @Override
  public String name() {
    return "query_erp_vouchers";
  }

  @Override
  public String description() {
    return "Liệt kê chứng từ trong ERP theo kỳ. Input: entityType (PURCHASE_INVOICE mặc định, SALES_INVOICE, PAYMENT), periodFrom, periodTo (yyyy-MM-dd, tối đa 12 tháng),"
        + " limit (1-50, mặc định 20). Output: tổng số và danh sách (mã, số chứng từ, ngày, đối tượng, tổng tiền).";
  }

  @Override
  public ToolResult execute(CurrentUser user, Map<String, Object> args) {
    var from = ToolArgs.date(args, "periodFrom", true);
    var to = ToolArgs.date(args, "periodTo", true);
    int limit = ToolArgs.limit(args, "limit", 20, 50);
    var page = erp.listVouchers(ToolArgs.entityType(args), user.companyId(), from, to, 0, limit);
    var sb =
        new StringBuilder("Tổng số: ")
            .append(page.totalItems())
            .append(", hiển thị ")
            .append(page.items().size())
            .append('\n');
    for (var v : page.items()) {
      sb.append("- ")
          .append(v.ref())
          .append(" | ")
          .append(v.voucherNo())
          .append(" | ")
          .append(v.voucherDate())
          .append(" | ")
          .append(v.partner() == null ? "" : v.partner().name())
          .append(
              v.partner() == null || v.partner().taxCode() == null
                  ? ""
                  : " (MST " + v.partner().taxCode() + ")")
          .append(" | ")
          .append(v.totalAmount().toPlainString())
          .append('\n');
    }
    var names =
        page.items().stream()
            .filter(v -> v.partner() != null)
            .map(v -> v.partner().name())
            .toList();
    return ToolResult.ok(
            page.totalItems()
                + " chứng từ "
                + ToolArgs.entityType(args)
                + " từ "
                + from
                + " đến "
                + to,
            sb.toString())
        .withSensitive(names);
  }
}
