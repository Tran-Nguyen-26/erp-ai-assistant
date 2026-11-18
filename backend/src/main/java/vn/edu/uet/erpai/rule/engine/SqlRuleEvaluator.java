package vn.edu.uet.erpai.rule.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.erp.model.ErpInvoice;
import vn.edu.uet.erpai.erp.model.ErpJournalLine;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.rule.definition.RuleDefinition;

/**
 * Đánh giá rule liên chứng từ / thống kê lịch sử bằng truy vấn ERP chỉ-đọc ({@code sqlRef}). Có
 * hàng trả về = vi phạm. Tham số lấy từ chứng từ (và tham số của rule) theo danh sách cho phép,
 * truyền dạng tham số có tên (không nối chuỗi). Với {@code forEach: lines} truy vấn chạy cho từng
 * dòng hàng.
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
    if (!rule.appliesTo(voucher.entityType())) {
      return List.of();
    }
    var results = new ArrayList<RuleResult>();
    if ("lines".equals(rule.forEach())) {
      for (var line : voucher.lines()) {
        evaluateOne(rule, voucher, line).ifPresent(results::add);
      }
    } else {
      evaluateOne(rule, voucher, null).ifPresent(results::add);
    }
    return results;
  }

  private java.util.Optional<RuleResult> evaluateOne(
      RuleDefinition rule, ErpVoucher voucher, ErpJournalLine line) {
    var params = params(rule, voucher, line);
    if (params == null) {
      return java.util.Optional.empty(); // thiếu dữ liệu để đối chiếu
    }
    var rows = erp.queryRule(rule.sqlRef(), params);
    if (rows.isEmpty()) {
      return java.util.Optional.empty();
    }
    var first = rows.get(0);
    var evidence = new LinkedHashMap<String, Object>();
    evidence.put("matchCount", rows.size());
    first.forEach((k, v) -> evidence.put(k, v));
    if (line != null) {
      evidence.put("lineNo", line.lineNo());
    }
    var scope = new LinkedHashMap<String, Object>();
    scope.put("entity", voucher);
    scope.put("invoice", voucher.invoice() == null ? Map.of() : voucher.invoice());
    scope.put("item", line == null ? Map.of() : line);
    scope.put("row", first);
    scope.put("dup", first); // tên cũ, dùng cho rule trùng hóa đơn
    scope.put("params", rule.params());
    var key = line == null ? invoiceKey(voucher.invoice()) : "line" + line.lineNo();
    return java.util.Optional.of(
        new RuleResult(
            rule.ruleId(),
            rule.severity(),
            voucher.ref(),
            renderer.render(rule.message(), scope),
            evidence,
            key,
            Fingerprint.of(rule.ruleId(), voucher.entityType().name(), voucher.ref(), key)));
  }

  /**
   * @return null nếu thiếu dữ liệu bắt buộc (khi đó rule không áp dụng được)
   */
  private Map<String, Object> params(RuleDefinition rule, ErpVoucher v, ErpJournalLine line) {
    var invoice = v.invoice();
    var out = new LinkedHashMap<String, Object>();
    for (var name : rule.sqlParams()) {
      Object value =
          switch (name) {
            case "companyId" -> v.companyId();
            case "entityRef" -> v.ref();
            case "sellerTaxCode" -> invoice == null ? null : invoice.sellerTaxCode();
            case "invoiceSeries" -> invoice == null ? null : invoice.invoiceSeries();
            case "invoiceNo" -> invoice == null ? null : invoice.invoiceNo();
            case "partnerId" -> v.partner() == null ? null : v.partner().id();
            case "sinceDate" ->
                v.voucherDate() == null
                    ? null
                    : java.sql.Date.valueOf(
                        v.voucherDate()
                            .minusMonths(
                                ((Number) rule.params().getOrDefault("lookbackMonths", 12))
                                    .longValue()));
            case "voucherDate" ->
                v.voucherDate() == null ? null : java.sql.Date.valueOf(v.voucherDate());
            case "debtPaymentAmount" -> debtPayment(v);
            case "lineNo" -> line == null ? null : line.lineNo();
            case "materialGoodsId" -> line == null ? null : line.materialGoodsId();
            case "unitPrice" -> line == null ? null : line.unitPrice();
            default -> rule.params().get(name); // tham số cấu hình của rule (ví dụ k, minSamples)
          };
      if (value == null || (value instanceof String s && s.isBlank())) {
        if (isRequired(name)) {
          return null;
        }
      }
      if (value instanceof BigDecimal b && "unitPrice".equals(name) && b.signum() <= 0) {
        return null; // đơn giá không dương: không đối chiếu thống kê
      }
      out.put(name, value);
    }
    return out;
  }

  /**
   * Tổng số tiền các dòng ghi Nợ tài khoản phải trả người bán (331*); không có → null (rule không
   * áp dụng).
   */
  private static BigDecimal debtPayment(ErpVoucher v) {
    var sum =
        v.lines().stream()
            .filter(l -> l.debitAccount() != null && l.debitAccount().startsWith("331"))
            .map(ErpJournalLine::amount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.signum() > 0 ? sum : null;
  }

  private static boolean isRequired(String name) {
    return !"entityRef".equals(name);
  }

  private static String invoiceKey(ErpInvoice i) {
    return i == null ? "" : i.sellerTaxCode() + "|" + i.invoiceSeries() + "|" + i.invoiceNo();
  }
}
