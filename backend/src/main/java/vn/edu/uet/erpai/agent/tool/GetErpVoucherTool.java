package vn.edu.uet.erpai.agent.tool;

import java.util.Map;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.erp.service.ErpQueryService;

@Component
public class GetErpVoucherTool implements AgentTool {

  private final ErpQueryService erp;

  public GetErpVoucherTool(ErpQueryService erp) {
    this.erp = erp;
  }

  @Override
  public String name() {
    return "get_erp_voucher";
  }

  @Override
  public String description() {
    return "Lấy chi tiết một hóa đơn mua hàng. Input: voucherRef (GUID). Output: thông tin chứng từ, đối tượng, hóa đơn và các dòng.";
  }

  @Override
  public ToolResult execute(CurrentUser user, Map<String, Object> args) {
    var v = erp.getPurchaseInvoice(user.companyId(), ToolArgs.string(args, "voucherRef", true));
    var sb = new StringBuilder();
    sb.append("Chứng từ ")
        .append(v.voucherNo())
        .append(" ngày ")
        .append(v.voucherDate())
        .append('\n')
        .append("Diễn giải: ")
        .append(v.description())
        .append('\n')
        .append("Tổng tiền: ")
        .append(v.totalAmount().toPlainString())
        .append(", thuế: ")
        .append(v.totalVatAmount().toPlainString())
        .append('\n');
    if (v.partner() != null) {
      sb.append("Đối tượng: ")
          .append(v.partner().name())
          .append(" (MST ")
          .append(v.partner().taxCode())
          .append(")\n");
    }
    if (v.invoice() != null) {
      sb.append("Hóa đơn: ")
          .append(v.invoice().invoiceSeries())
          .append('-')
          .append(v.invoice().invoiceNo())
          .append('\n');
    }
    v.lines()
        .forEach(
            l ->
                sb.append("Dòng ")
                    .append(l.lineNo())
                    .append(": ")
                    .append(l.description())
                    .append(" | Nợ ")
                    .append(l.debitAccount())
                    .append(" Có ")
                    .append(l.creditAccount())
                    .append(" | ")
                    .append(l.amount().toPlainString())
                    .append(" | thuế ")
                    .append(l.vatAmount().toPlainString())
                    .append('\n'));
    return ToolResult.ok(
            "Chứng từ " + v.voucherNo() + " (" + v.lines().size() + " dòng)", sb.toString())
        .withSensitive(
            v.partner() == null ? java.util.List.of() : java.util.List.of(v.partner().name()));
  }
}
