package vn.edu.uet.erpai.entry.extractor;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dữ liệu hóa đơn trích xuất. Tiền/số lượng dạng chuỗi decimal khi serialize (docs/api-spec.md
 * §1.2).
 */
public record ExtractedInvoice(
    String invoiceTemplate,
    String invoiceSeries,
    String invoiceNo,
    LocalDate invoiceDate,
    String sellerName,
    String sellerTaxCode,
    String buyerName,
    String buyerTaxCode,
    String currency,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal exchangeRate,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalBeforeTax,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalVat,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalPayable,
    String paymentMethod,
    List<Line> lines) {

  /**
   * {@code vatRate} là phần trăm (10 = 10%); null khi không chịu thuế / không kê khai ({@code
   * vatLabel}).
   */
  public record Line(
      int lineNo,
      String description,
      String unit,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal vatRate,
      String vatLabel,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal vatAmount) {}

  public ExtractedInvoice {
    lines = lines == null ? List.of() : List.copyOf(lines);
  }
}
