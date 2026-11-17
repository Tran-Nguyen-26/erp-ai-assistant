package vn.edu.uet.erpai.erp.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import vn.edu.uet.erpai.erp.model.ErpInvoice;
import vn.edu.uet.erpai.erp.model.ErpJournalLine;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;

/** DTO theo docs/api-spec.md §3: tiền là string decimal 2 chữ số. */
public record ErpVoucherResponse(
    String entityRef,
    String entityType,
    String voucherNo,
    LocalDate voucherDate,
    LocalDate postedDate,
    String description,
    Partner partner,
    String currency,
    BigDecimal exchangeRate,
    String totalAmount,
    Invoice invoice,
    List<Line> lines,
    String status) {

  public record Partner(String code, String name, String taxCode) {}

  public record Invoice(
      String invoiceTemplate,
      String invoiceSeries,
      String invoiceNo,
      LocalDate invoiceDate,
      String sellerTaxCode,
      String amount,
      String vatAmount,
      String discountAmount,
      String totalAmount,
      String paymentMethod) {}

  public record Line(
      int lineNo,
      String description,
      String debitAccount,
      String creditAccount,
      String amount,
      BigDecimal quantity,
      String unitPrice,
      BigDecimal vatRate,
      String vatAmount,
      String vatAccount) {}

  public static ErpVoucherResponse from(ErpVoucher v) {
    return new ErpVoucherResponse(
        v.ref(),
        v.entityType().name(),
        v.voucherNo(),
        v.voucherDate(),
        v.postedDate(),
        v.description(),
        partner(v.partner()),
        v.currency(),
        v.exchangeRate(),
        money(v.totalAmount()),
        invoice(v.invoice()),
        v.lines().stream().map(ErpVoucherResponse::line).toList(),
        v.isRecorded() ? "POSTED" : "UNPOSTED");
  }

  private static Partner partner(ErpPartnerRef p) {
    return p == null ? null : new Partner(p.code(), p.name(), p.taxCode());
  }

  private static Invoice invoice(ErpInvoice i) {
    return i == null
        ? null
        : new Invoice(
            i.invoiceTemplate(),
            i.invoiceSeries(),
            i.invoiceNo(),
            i.invoiceDate(),
            i.sellerTaxCode(),
            money(i.amount()),
            money(i.vatAmount()),
            money(i.discountAmount()),
            money(i.totalAmount()),
            i.paymentMethod());
  }

  private static Line line(ErpJournalLine l) {
    return new Line(
        l.lineNo(),
        l.description(),
        l.debitAccount(),
        l.creditAccount(),
        money(l.amount()),
        l.quantity(),
        money(l.unitPrice()),
        l.vatRate(),
        money(l.vatAmount()),
        l.vatAccount());
  }

  static String money(BigDecimal v) {
    return v == null ? null : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }
}
