package vn.edu.uet.erpai.erp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Chứng từ chuẩn hóa (canonical). Không chứa tên cột ERP. */
public record ErpVoucher(
    String ref,
    String companyId,
    ErpEntityType entityType,
    int voucherTypeId,
    String voucherNo,
    LocalDate voucherDate,
    LocalDate postedDate,
    String description,
    ErpPartnerRef partner,
    String currency,
    BigDecimal exchangeRate,
    BigDecimal totalAmount,
    BigDecimal totalVatAmount,
    BigDecimal totalDiscountAmount,
    boolean isRecorded,
    List<ErpJournalLine> lines,
    ErpInvoice invoice) {

  public ErpVoucher {
    lines = lines == null ? List.of() : List.copyOf(lines);
  }

  public ErpVoucher withLines(List<ErpJournalLine> newLines, ErpInvoice newInvoice) {
    return new ErpVoucher(
        ref,
        companyId,
        entityType,
        voucherTypeId,
        voucherNo,
        voucherDate,
        postedDate,
        description,
        partner,
        currency,
        exchangeRate,
        totalAmount,
        totalVatAmount,
        totalDiscountAmount,
        isRecorded,
        newLines,
        newInvoice);
  }
}
