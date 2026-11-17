package vn.edu.uet.erpai.entry.extractor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Độ tin cậy từng trường cho dữ liệu do LLM trích xuất: điểm của mô hình (tối đa 0.95, vì không bao
 * giờ chắc chắn tuyệt đối như XML) kết hợp kiểm tra số học và chữ số kiểm tra MST. Trường thiếu =
 * 0.
 */
public final class ConfidenceScorer {

  static final double MODEL_CAP = 0.95;
  static final double DEFAULT = 0.5;
  private static final BigDecimal TOLERANCE = BigDecimal.ONE;

  private ConfidenceScorer() {}

  public static Map<String, Double> score(ExtractedInvoice inv, Map<String, Double> model) {
    var c = new LinkedHashMap<String, Double>();
    put(c, model, "invoiceTemplate", inv.invoiceTemplate());
    put(c, model, "invoiceSeries", inv.invoiceSeries());
    put(c, model, "invoiceNo", inv.invoiceNo());
    put(c, model, "invoiceDate", inv.invoiceDate());
    put(c, model, "sellerName", inv.sellerName());
    put(c, model, "sellerTaxCode", inv.sellerTaxCode());
    put(c, model, "buyerTaxCode", inv.buyerTaxCode());
    put(c, model, "totalBeforeTax", inv.totalBeforeTax());
    put(c, model, "totalVat", inv.totalVat());
    put(c, model, "totalPayable", inv.totalPayable());
    put(c, model, "lines", inv.lines().isEmpty() ? null : inv.lines());

    // MST sai định dạng hoặc sai chữ số kiểm tra → khả năng cao nhận dạng sai chữ số
    lower(c, "sellerTaxCode", validTaxCode(inv.sellerTaxCode()) ? 1.0 : 0.5);
    lower(c, "buyerTaxCode", validTaxCode(inv.buyerTaxCode()) ? 1.0 : 0.5);
    if (inv.sellerTaxCode() != null
        && inv.sellerTaxCode().replaceAll("[^0-9]", "").length() == 10
        && !VnParsing.taxCodeChecksumOk(inv.sellerTaxCode())) {
      lower(c, "sellerTaxCode", 0.6);
    }

    // tổng các dòng phải khớp tiền hàng
    var sumLines =
        inv.lines().stream()
            .map(ExtractedInvoice.Line::amount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (inv.totalBeforeTax() != null
        && !inv.lines().isEmpty()
        && sumLines.subtract(inv.totalBeforeTax()).abs().compareTo(TOLERANCE) > 0) {
      lower(c, "totalBeforeTax", 0.4);
      lower(c, "lines", 0.4);
    }
    // tiền hàng + thuế phải bằng tổng thanh toán
    if (inv.totalBeforeTax() != null
        && inv.totalVat() != null
        && inv.totalPayable() != null
        && inv.totalBeforeTax()
                .add(inv.totalVat())
                .subtract(inv.totalPayable())
                .abs()
                .compareTo(TOLERANCE)
            > 0) {
      lower(c, "totalPayable", 0.4);
      lower(c, "totalVat", 0.4);
      lower(c, "totalBeforeTax", 0.4);
    }
    return c;
  }

  private static boolean validTaxCode(String s) {
    return s == null || s.matches("\\d{10}|\\d{10}-\\d{3}|\\d{12}");
  }

  private static void put(
      Map<String, Double> out, Map<String, Double> model, String field, Object value) {
    if (value == null) {
      out.put(field, 0.0);
      return;
    }
    double m = model == null || model.get(field) == null ? DEFAULT : model.get(field);
    out.put(field, Math.min(MODEL_CAP, Math.max(0.0, m)));
  }

  private static void lower(Map<String, Double> c, String field, double ceiling) {
    c.computeIfPresent(field, (k, v) -> v == 0.0 ? v : Math.min(v, ceiling));
  }
}
