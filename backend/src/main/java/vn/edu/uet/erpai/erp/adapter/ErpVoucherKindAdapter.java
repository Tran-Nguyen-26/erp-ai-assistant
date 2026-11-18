package vn.edu.uet.erpai.erp.adapter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpInvoice;
import vn.edu.uet.erpai.erp.model.ErpJournalLine;
import vn.edu.uet.erpai.erp.model.ErpPage;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.model.VoucherFilter;

/**
 * Adapter dùng chung cho các chứng từ có cấu trúc header + dòng chi tiết đơn giản (khóa chính là
 * ID): phiếu chi/thu tiền mặt, báo Nợ/Có ngân hàng, hàng mua/bán trả lại. Mỗi loại có bộ file SQL
 * {@code <prefix>-by-id|lines|list|count|ids.sql} trả về CÙNG bộ alias cột (cột không có ở bảng gốc
 * được trả về NULL) nên dùng chung RowMapper. Chỉ SELECT.
 */
@Component
public class ErpVoucherKindAdapter {

  /**
   * @param partnerIsSeller đối tượng là bên bán trên hóa đơn (chi trả NCC, hàng mua trả lại) → MST
   *     đối tác được điền vào {@code ErpInvoice.sellerTaxCode}
   * @param defaultPayment phương thức thanh toán khi bảng không có cột PaymentMethod ({@code null}
   *     = suy ra theo TypeID)
   */
  public enum Kind {
    PAYMENT(ErpEntityType.PAYMENT, "payment", range(110, 118), true, "CASH"),
    RECEIPT(ErpEntityType.RECEIPT, "receipt", range(100, 104), false, "CASH"),
    BANK_DEBIT(
        ErpEntityType.BANK_DEBIT,
        "bank-debit",
        concat(range(120, 128), range(130, 135), range(140, 145)),
        true,
        null),
    BANK_CREDIT(ErpEntityType.BANK_CREDIT, "bank-credit", range(160, 164), false, "BANK_TRANSFER"),
    PURCHASE_RETURN(
        ErpEntityType.PURCHASE_RETURN,
        "purchase-return",
        List.of(220, 221, 222, 230, 231, 232),
        true,
        null),
    SALES_RETURN(ErpEntityType.SALES_RETURN, "sales-return", range(330, 335), false, null);

    final ErpEntityType entityType;
    final String sqlPrefix;
    final List<Integer> typeIds;
    final boolean partnerIsSeller;
    final String defaultPayment;

    Kind(
        ErpEntityType entityType,
        String sqlPrefix,
        List<Integer> typeIds,
        boolean partnerIsSeller,
        String defaultPayment) {
      this.entityType = entityType;
      this.sqlPrefix = sqlPrefix;
      this.typeIds = typeIds;
      this.partnerIsSeller = partnerIsSeller;
      this.defaultPayment = defaultPayment;
    }

    public ErpEntityType entityType() {
      return entityType;
    }

    public List<Integer> typeIds() {
      return typeIds;
    }

    public static Optional<Kind> of(ErpEntityType type) {
      for (var k : values()) {
        if (k.entityType == type) {
          return Optional.of(k);
        }
      }
      return Optional.empty();
    }

    private static List<Integer> range(int from, int toInclusive) {
      var out = new ArrayList<Integer>();
      for (int i = from; i <= toInclusive; i++) {
        out.add(i);
      }
      return out;
    }

    @SafeVarargs
    private static List<Integer> concat(List<Integer>... parts) {
      var out = new ArrayList<Integer>();
      for (var p : parts) {
        out.addAll(p);
      }
      return out;
    }
  }

  public record HeaderRow(ErpVoucher voucher, ErpInvoice invoice) {}

  public record LineRow(
      ErpJournalLine line,
      String invoiceTemplate,
      String invoiceSeries,
      String invoiceNo,
      LocalDate invoiceDate) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final SqlLoader sql;

  public ErpVoucherKindAdapter(NamedParameterJdbcTemplate erpJdbcTemplate, SqlLoader sql) {
    this.jdbc = erpJdbcTemplate;
    this.sql = sql;
  }

  public Optional<ErpVoucher> find(Kind kind, String companyId, String id) {
    var params = new MapSqlParameterSource("companyId", companyId).addValue("id", id);
    var header =
        jdbc.query(sql.load(kind.sqlPrefix + "-by-id"), params, headerMapper(kind)).stream()
            .findFirst();
    if (header.isEmpty()) {
      return Optional.empty();
    }
    var h = header.get();
    var rows = jdbc.query(sql.load(kind.sqlPrefix + "-lines"), params, LINE_MAPPER);
    var lines = new ArrayList<ErpJournalLine>();
    ErpInvoice invoice = h.invoice();
    for (var row : rows) {
      lines.add(row.line());
      if (invoice == null && row.invoiceNo() != null && !row.invoiceNo().isBlank()) {
        invoice =
            invoiceOf(
                kind,
                h.voucher(),
                row.invoiceTemplate(),
                row.invoiceSeries(),
                row.invoiceNo(),
                row.invoiceDate(),
                null);
      }
    }
    return Optional.of(h.voucher().withLines(lines, invoice));
  }

  public ErpPage<ErpVoucher> list(
      Kind kind, String companyId, LocalDate from, LocalDate toInclusive, int page, int size) {
    return list(kind, companyId, from, toInclusive, page, size, VoucherFilter.NONE);
  }

  public ErpPage<ErpVoucher> list(
      Kind kind,
      String companyId,
      LocalDate from,
      LocalDate toInclusive,
      int page,
      int size,
      VoucherFilter filter) {
    var count =
        jdbc.queryForObject(
            sql.load(kind.sqlPrefix + "-count"),
            filter.applyTo(period(kind, companyId, from, toInclusive)),
            Long.class);
    var params =
        filter
            .applyTo(period(kind, companyId, from, toInclusive))
            .addValue("offset", (long) page * size)
            .addValue("size", size);
    var items =
        jdbc.query(sql.load(kind.sqlPrefix + "-list"), params, headerMapper(kind)).stream()
            .map(HeaderRow::voucher)
            .toList();
    return new ErpPage<>(items, page, size, count);
  }

  public long count(Kind kind, String companyId, LocalDate from, LocalDate toInclusive) {
    return jdbc.queryForObject(
        sql.load(kind.sqlPrefix + "-count"),
        VoucherFilter.NONE.applyTo(period(kind, companyId, from, toInclusive)),
        Long.class);
  }

  public List<String> ids(
      Kind kind, String companyId, LocalDate from, LocalDate toInclusive, long offset, int size) {
    var params =
        period(kind, companyId, from, toInclusive)
            .addValue("offset", offset)
            .addValue("size", size);
    return jdbc.query(sql.load(kind.sqlPrefix + "-ids"), params, (rs, i) -> rs.getString("ID"));
  }

  private static MapSqlParameterSource period(
      Kind kind, String companyId, LocalDate from, LocalDate toInclusive) {
    return new MapSqlParameterSource("companyId", companyId)
        .addValue("periodFrom", java.sql.Date.valueOf(from))
        .addValue("periodToExclusive", java.sql.Date.valueOf(toInclusive.plusDays(1)))
        .addValue("typeIds", kind.typeIds);
  }

  // ---------- RowMapper (public static để test bằng ResultSet giả) ----------

  public static RowMapper<HeaderRow> headerMapper(Kind kind) {
    return (rs, i) -> {
      var total = ErpVoucherAdapter.money(rs, "TotalAmount");
      var vat = ErpVoucherAdapter.money(rs, "TotalVATAmount");
      var discount = ErpVoucherAdapter.money(rs, "TotalDiscountAmount");
      int typeId = rs.getInt("TypeID");
      var voucher =
          new ErpVoucher(
              rs.getString("ID"),
              rs.getString("CompanyID"),
              kind.entityType,
              typeId,
              rs.getString("NoFBook"),
              ErpVoucherAdapter.date(rs, "Date"),
              ErpVoucherAdapter.date(rs, "PostedDate"),
              rs.getString("Reason"),
              new ErpPartnerRef(
                  rs.getString("AccountingObjectID"),
                  rs.getString("PartnerCode"),
                  rs.getString("AccountingObjectName"),
                  rs.getString("PartnerTaxCode")),
              rs.getString("CurrencyID"),
              ErpVoucherAdapter.money(rs, "ExchangeRate"),
              total,
              vat,
              discount,
              rs.getBoolean("Recorded"),
              List.of(),
              null);
      var invoiceNo = rs.getString("InvoiceNo");
      ErpInvoice invoice = null;
      if (invoiceNo != null && !invoiceNo.isBlank()) {
        invoice =
            invoiceOf(
                kind,
                voucher,
                rs.getString("InvoiceTemplate"),
                rs.getString("InvoiceSeries"),
                invoiceNo,
                ErpVoucherAdapter.date(rs, "InvoiceDate"),
                rs.getString("PaymentMethod"));
      }
      return new HeaderRow(voucher, invoice);
    };
  }

  static ErpInvoice invoiceOf(
      Kind kind,
      ErpVoucher v,
      String template,
      String series,
      String no,
      LocalDate date,
      String paymentMethod) {
    var method =
        paymentMethod != null && !paymentMethod.isBlank()
            ? ErpSalesPaymentAdapter.normalizePayment(paymentMethod)
            : kind.defaultPayment != null
                ? kind.defaultPayment
                : (v.voucherTypeId() >= 140 && v.voucherTypeId() <= 145 ? "CASH" : "BANK_TRANSFER");
    var partner = v.partner();
    return new ErpInvoice(
        template,
        series,
        no,
        date,
        kind.partnerIsSeller && partner != null ? partner.taxCode() : null,
        kind.partnerIsSeller && partner != null ? partner.name() : null,
        v.totalAmount().subtract(v.totalVatAmount()).add(v.totalDiscountAmount()),
        v.totalVatAmount(),
        v.totalDiscountAmount(),
        v.totalAmount(),
        method);
  }

  public static final RowMapper<LineRow> LINE_MAPPER =
      (rs, i) -> {
        var line =
            new ErpJournalLine(
                i + 1,
                rs.getString("Description"),
                rs.getString("DebitAccount"),
                rs.getString("CreditAccount"),
                ErpVoucherAdapter.money(rs, "Amount"),
                ErpVoucherAdapter.money(rs, "AmountOriginal"),
                rs.getString("MaterialGoodsID"),
                ErpVoucherAdapter.money(rs, "Quantity"),
                ErpVoucherAdapter.money(rs, "UnitPrice"),
                ErpVoucherAdapter.money(rs, "VATRate"),
                ErpVoucherAdapter.money(rs, "VATAmount"),
                rs.getString("VATAccount"));
        return new LineRow(
            line,
            rs.getString("InvoiceTemplate"),
            rs.getString("InvoiceSeries"),
            rs.getString("InvoiceNo"),
            ErpVoucherAdapter.date(rs, "InvoiceDate"));
      };
}
