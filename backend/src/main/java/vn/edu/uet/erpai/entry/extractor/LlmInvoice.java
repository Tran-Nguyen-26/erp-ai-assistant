package vn.edu.uet.erpai.entry.extractor;

import java.util.List;
import java.util.Map;

/** Cấu trúc LLM trả về khi trích xuất hóa đơn (mọi giá trị dạng chuỗi, backend tự chuyển kiểu). */
public record LlmInvoice(
    String invoiceTemplate,
    String invoiceSeries,
    String invoiceNo,
    String invoiceDate,
    String sellerName,
    String sellerTaxCode,
    String buyerName,
    String buyerTaxCode,
    String currency,
    String totalBeforeTax,
    String totalVat,
    String totalPayable,
    String paymentMethod,
    List<Line> lines,
    Map<String, Double> confidence) {

  public record Line(
      String description,
      String unit,
      String quantity,
      String unitPrice,
      String amount,
      String vatRate) {}
}
