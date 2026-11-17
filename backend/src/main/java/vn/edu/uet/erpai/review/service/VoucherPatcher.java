package vn.edu.uet.erpai.review.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.erp.model.ErpJournalLine;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;

/**
 * Đọc/áp thay đổi theo đường dẫn ({@code lines[0].vatAmount}, {@code partner.taxCode}...) lên BẢN
 * SAO bất biến của chứng từ. Chỉ các đường dẫn trong danh sách cho phép; không bao giờ ghi vào ERP.
 */
@Component
public class VoucherPatcher {

  private static final Pattern LINE_PATH = Pattern.compile("^lines\\[(\\d+)]\\.(\\w+)$");

  /** Giá trị hiện tại dưới dạng chuỗi (để ghi vào {@code from}). */
  public String read(ErpVoucher v, String path) {
    var m = LINE_PATH.matcher(path);
    if (m.matches()) {
      var line = line(v, Integer.parseInt(m.group(1)), path);
      return str(
          switch (m.group(2)) {
            case "description" -> line.description();
            case "debitAccount" -> line.debitAccount();
            case "creditAccount" -> line.creditAccount();
            case "vatAccount" -> line.vatAccount();
            case "amount" -> line.amount();
            case "amountOriginal" -> line.amountOriginal();
            case "quantity" -> line.quantity();
            case "unitPrice" -> line.unitPrice();
            case "vatRate" -> line.vatRate();
            case "vatAmount" -> line.vatAmount();
            default -> throw invalid(path);
          });
    }
    return str(
        switch (path) {
          case "voucherNo" -> v.voucherNo();
          case "voucherDate" -> v.voucherDate();
          case "description" -> v.description();
          case "totalAmount" -> v.totalAmount();
          case "totalVatAmount" -> v.totalVatAmount();
          case "totalDiscountAmount" -> v.totalDiscountAmount();
          case "partner.taxCode" -> v.partner() == null ? null : v.partner().taxCode();
          case "partner.name" -> v.partner() == null ? null : v.partner().name();
          default -> throw invalid(path);
        });
  }

  public ErpVoucher apply(ErpVoucher v, String path, String to) {
    var m = LINE_PATH.matcher(path);
    if (m.matches()) {
      int idx = Integer.parseInt(m.group(1));
      var old = line(v, idx, path);
      var lines = new ArrayList<>(v.lines());
      lines.set(idx, withLine(old, m.group(2), to, path));
      return v.withLines(lines, v.invoice());
    }
    return switch (path) {
      case "voucherNo" -> copy(v, to, null, null, null, null, null, null);
      case "voucherDate" -> copy(v, null, date(to, path), null, null, null, null, null);
      case "description" -> copy(v, null, null, to, null, null, null, null);
      case "totalAmount" -> copy(v, null, null, null, dec(to, path), null, null, null);
      case "totalVatAmount" -> copy(v, null, null, null, null, dec(to, path), null, null);
      case "totalDiscountAmount" -> copy(v, null, null, null, null, null, dec(to, path), null);
      case "partner.taxCode" ->
          copy(v, null, null, null, null, null, null, partner(v, "taxCode", to));
      case "partner.name" -> copy(v, null, null, null, null, null, null, partner(v, "name", to));
      default -> throw invalid(path);
    };
  }

  private static ErpVoucher copy(
      ErpVoucher v,
      String voucherNo,
      LocalDate voucherDate,
      String description,
      BigDecimal total,
      BigDecimal totalVat,
      BigDecimal totalDiscount,
      ErpPartnerRef partner) {
    return new ErpVoucher(
        v.ref(),
        v.companyId(),
        v.entityType(),
        v.voucherTypeId(),
        voucherNo != null ? voucherNo : v.voucherNo(),
        voucherDate != null ? voucherDate : v.voucherDate(),
        v.postedDate(),
        description != null ? description : v.description(),
        partner != null ? partner : v.partner(),
        v.currency(),
        v.exchangeRate(),
        total != null ? total : v.totalAmount(),
        totalVat != null ? totalVat : v.totalVatAmount(),
        totalDiscount != null ? totalDiscount : v.totalDiscountAmount(),
        v.isRecorded(),
        v.lines(),
        v.invoice());
  }

  private static ErpPartnerRef partner(ErpVoucher v, String field, String to) {
    var p = v.partner() == null ? new ErpPartnerRef(null, null, null, null) : v.partner();
    return new ErpPartnerRef(
        p.id(),
        p.code(),
        field.equals("name") ? to : p.name(),
        field.equals("taxCode") ? to : p.taxCode());
  }

  private static ErpJournalLine withLine(ErpJournalLine l, String field, String to, String path) {
    return switch (field) {
      case "description" ->
          new ErpJournalLine(
              l.lineNo(),
              to,
              l.debitAccount(),
              l.creditAccount(),
              l.amount(),
              l.amountOriginal(),
              l.materialGoodsId(),
              l.quantity(),
              l.unitPrice(),
              l.vatRate(),
              l.vatAmount(),
              l.vatAccount());
      case "debitAccount" ->
          new ErpJournalLine(
              l.lineNo(),
              l.description(),
              to,
              l.creditAccount(),
              l.amount(),
              l.amountOriginal(),
              l.materialGoodsId(),
              l.quantity(),
              l.unitPrice(),
              l.vatRate(),
              l.vatAmount(),
              l.vatAccount());
      case "creditAccount" ->
          new ErpJournalLine(
              l.lineNo(),
              l.description(),
              l.debitAccount(),
              to,
              l.amount(),
              l.amountOriginal(),
              l.materialGoodsId(),
              l.quantity(),
              l.unitPrice(),
              l.vatRate(),
              l.vatAmount(),
              l.vatAccount());
      case "vatAccount" ->
          new ErpJournalLine(
              l.lineNo(),
              l.description(),
              l.debitAccount(),
              l.creditAccount(),
              l.amount(),
              l.amountOriginal(),
              l.materialGoodsId(),
              l.quantity(),
              l.unitPrice(),
              l.vatRate(),
              l.vatAmount(),
              to);
      case "amount" ->
          new ErpJournalLine(
              l.lineNo(),
              l.description(),
              l.debitAccount(),
              l.creditAccount(),
              dec(to, path),
              l.amountOriginal(),
              l.materialGoodsId(),
              l.quantity(),
              l.unitPrice(),
              l.vatRate(),
              l.vatAmount(),
              l.vatAccount());
      case "amountOriginal" ->
          new ErpJournalLine(
              l.lineNo(),
              l.description(),
              l.debitAccount(),
              l.creditAccount(),
              l.amount(),
              dec(to, path),
              l.materialGoodsId(),
              l.quantity(),
              l.unitPrice(),
              l.vatRate(),
              l.vatAmount(),
              l.vatAccount());
      case "quantity" ->
          new ErpJournalLine(
              l.lineNo(),
              l.description(),
              l.debitAccount(),
              l.creditAccount(),
              l.amount(),
              l.amountOriginal(),
              l.materialGoodsId(),
              dec(to, path),
              l.unitPrice(),
              l.vatRate(),
              l.vatAmount(),
              l.vatAccount());
      case "unitPrice" ->
          new ErpJournalLine(
              l.lineNo(),
              l.description(),
              l.debitAccount(),
              l.creditAccount(),
              l.amount(),
              l.amountOriginal(),
              l.materialGoodsId(),
              l.quantity(),
              dec(to, path),
              l.vatRate(),
              l.vatAmount(),
              l.vatAccount());
      case "vatRate" ->
          new ErpJournalLine(
              l.lineNo(),
              l.description(),
              l.debitAccount(),
              l.creditAccount(),
              l.amount(),
              l.amountOriginal(),
              l.materialGoodsId(),
              l.quantity(),
              l.unitPrice(),
              dec(to, path),
              l.vatAmount(),
              l.vatAccount());
      case "vatAmount" ->
          new ErpJournalLine(
              l.lineNo(),
              l.description(),
              l.debitAccount(),
              l.creditAccount(),
              l.amount(),
              l.amountOriginal(),
              l.materialGoodsId(),
              l.quantity(),
              l.unitPrice(),
              l.vatRate(),
              dec(to, path),
              l.vatAccount());
      default -> throw invalid(path);
    };
  }

  private static ErpJournalLine line(ErpVoucher v, int idx, String path) {
    if (idx >= v.lines().size()) {
      throw invalid(path);
    }
    return v.lines().get(idx);
  }

  private static BigDecimal dec(String s, String path) {
    try {
      return new BigDecimal(s.trim());
    } catch (NumberFormatException | NullPointerException e) {
      throw new BusinessException("PROPOSAL_PATH_INVALID", "Giá trị không phải số cho " + path);
    }
  }

  private static LocalDate date(String s, String path) {
    try {
      return LocalDate.parse(s.trim());
    } catch (DateTimeParseException | NullPointerException e) {
      throw new BusinessException("PROPOSAL_PATH_INVALID", "Ngày phải dạng yyyy-MM-dd cho " + path);
    }
  }

  private static String str(Object o) {
    return o == null ? null : o instanceof BigDecimal b ? b.toPlainString() : o.toString();
  }

  private static BusinessException invalid(String path) {
    return new BusinessException(
        "PROPOSAL_PATH_INVALID", "Đường dẫn không hợp lệ hoặc không được phép sửa: " + path);
  }
}
