package vn.edu.uet.erpai.rule.engine;

import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.rule.definition.RuleDefinition;

/**
 * Đánh giá rule liên chứng từ bằng truy vấn ERP chỉ-đọc ({@code sqlRef}). Có kết quả trả về = vi
 * phạm. Tham số lấy từ chứng từ theo danh sách cho phép, truyền dạng tham số có tên (không nối
 * chuỗi).
 */
@Component
public class SqlRuleEvaluator {

  private final ErpQueryService erp;
  private final SpelRuleEvaluator renderer;

  public SqlRuleEvaluator(ErpQueryService erp, SpelRuleEvaluator renderer) {
    this.erp = erp;
    this.renderer = renderer;
  }

  public List<RuleResult> evaluate(RuleDefinition rule, ErpVoucher voucher) {
    if (rule.entityType() != voucher.entityType()) {
      return List.of();
    }
    var invoice = voucher.invoice();
    if (invoice == null
        || isBlank(invoice.invoiceNo())
        || isBlank(invoice.invoiceSeries())
        || isBlank(invoice.sellerTaxCode())) {
      return List.of(); // chưa đủ thông tin hóa đơn để đối chiếu
    }
    var params = new LinkedHashMap<String, Object>();
    for (var name : rule.sqlParams()) {
      params.put(
          name,
          switch (name) {
            case "companyId" -> voucher.companyId();
            case "sellerTaxCode" -> invoice.sellerTaxCode();
            case "invoiceSeries" -> invoice.invoiceSeries();
            case "invoiceNo" -> invoice.invoiceNo();
            case "entityRef" -> voucher.ref();
            default ->
                throw new IllegalStateException(rule.ruleId() + ": tham số không hỗ trợ " + name);
          });
    }
    var rows = erp.queryRule(rule.sqlRef(), params);
    if (rows.isEmpty()) {
      return List.of();
    }
    var first = rows.get(0);
    var evidence = new LinkedHashMap<String, Object>();
    evidence.put("duplicateCount", rows.size());
    evidence.put("duplicateRef", first.get("ref"));
    evidence.put("duplicateVoucherNo", first.get("voucherNo"));
    var scope = new LinkedHashMap<String, Object>();
    scope.put("entity", voucher);
    scope.put("invoice", invoice);
    scope.put("dup", first);
    scope.put("params", rule.params());
    var key = invoice.sellerTaxCode() + "|" + invoice.invoiceSeries() + "|" + invoice.invoiceNo();
    return List.of(
        new RuleResult(
            rule.ruleId(),
            rule.severity(),
            voucher.ref(),
            renderer.render(rule.message(), scope),
            evidence,
            key,
            Fingerprint.of(rule.ruleId(), voucher.entityType().name(), voucher.ref(), key)));
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
