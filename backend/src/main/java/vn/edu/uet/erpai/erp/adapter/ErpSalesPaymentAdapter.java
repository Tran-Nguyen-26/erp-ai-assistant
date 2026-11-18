package vn.edu.uet.erpai.erp.adapter;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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

/**
 * Adapter hóa đơn bán hàng (SAInvoice/SAInvoiceDetail) và phiếu chi (MCPayment/MCPaymentDetail) →
 * canonical model. Chỉ SELECT. Khóa hóa đơn bán hàng có dạng {@code GUID@yyyy-MM-dd} vì SAInvoice
 * được phân vùng/đánh index theo Date (xem docs/erp-mapping.md).
 */
@Component
public class ErpSalesPaymentAdapter {

  /** Bán hàng: chưa thu tiền (320), thu ngay tiền mặt (321) / chuyển khoản (322). */
  public static final List<Integer> SALES_TYPE_IDS = List.of(320, 321, 322);

  /** Phiếu chi tiền mặt: 110 (phiếu chi) đến 118. */
  public static final List<Integer> PAYMENT_TYPE_IDS =
      List.of(110, 111, 112, 113, 114, 115, 116, 117, 118);

  private final NamedParameterJdbcTemplate jdbc;
  private final SqlLoader sql;

  public ErpSalesPaymentAdapter(NamedParameterJdbcTemplate erpJdbcTemplate, SqlLoader sql) {
    this.jdbc = erpJdbcTemplate;
    this.sql = sql;
  }

  // ---------- hóa đơn bán hàng ----------

  /**
   * @param date ngày hóa đơn nếu biết (tra cứu nhanh theo PK (Date, ID))
   */
  public Optional<ErpVoucher> findSalesInvoice(
      String companyId, String id, Optional<LocalDate> date) {
    var params = new MapSqlParameterSource("companyId", companyId).addValue("id", id);
    String headerSql;
    if (date.isPresent()) {
      params
          .addValue("dateFrom", java.sql.Date.valueOf(date.get()))
          .addValue("dateTo", java.sql.Date.valueOf(date.get().plusDays(1)));
      headerSql = "sales-invoice-by-id";
    } else {
      headerSql = "sales-invoice-by-id-any-date";
    }
    var header = jdbc.query(sql.load(headerSql), params, SALES_MAPPER).stream().findFirst();
    if (header.isEmpty()) {
      return Optional.empty();
    }
    var v = header.get();
    var lineParams = new MapSqlParameterSource("companyId", companyId).addValue("id", id);
    lineParams.addValue("partitionDate", Timestamp.valueOf(v.voucherDate().atStartOfDay()));
    var lines = jdbc.query(sql.load("sales-invoice-lines"), lineParams, SALES_LINE_MAPPER);
    if (lines.isEmpty()) {
      // DatePartition có thể không trùng Date của header: thử lại không lọc phân vùng
      lines =
          jdbc.query(sql.load("sales-invoice-lines-any-partition"), lineParams, SALES_LINE_MAPPER);
    }
    return Optional.of(v.withLines(withLineNo(lines), v.invoice()));
  }

  public ErpPage<ErpVoucher> listSalesInvoices(
      String companyId, LocalDate from, LocalDate toInclusive, int page, int size) {
    return list(
        "sales-invoice", SALES_TYPE_IDS, SALES_MAPPER, companyId, from, toInclusive, page, size);
  }

  public long countSalesInvoices(String companyId, LocalDate from, LocalDate toInclusive) {
    return count("sales-invoice", SALES_TYPE_IDS, companyId, from, toInclusive);
  }

  public List<String> listSalesInvoiceIds(
      String companyId, LocalDate from, LocalDate toInclusive, long offset, int size) {
    return ids("sales-invoice", SALES_TYPE_IDS, companyId, from, toInclusive, offset, size);
  }

  // ---------- phiếu chi ----------

  public Optional<ErpVoucher> findPayment(String companyId, String id) {
    var params = new MapSqlParameterSource("companyId", companyId).addValue("id", id);
    var header = jdbc.query(sql.load("payment-by-id"), params, PAYMENT_MAPPER).stream().findFirst();
    if (header.isEmpty()) {
      return Optional.empty();
    }
    var v = header.get();
    var rows = jdbc.query(sql.load("payment-lines"), params, new PaymentLineMapper());
    var lines = new ArrayList<ErpJournalLine>();
    ErpInvoice invoice = null;
    for (var row : rows) {
      lines.add(row.line());
      if (invoice == null && row.invoiceNo() != null && !row.invoiceNo().isBlank()) {
        invoice =
            new ErpInvoice(
                row.invoiceTemplate(),
                row.invoiceSeries(),
                row.invoiceNo(),
                row.invoiceDate(),
                v.partner() == null ? null : v.partner().taxCode(),
                v.partner() == null ? null : v.partner().name(),
                v.totalAmount().subtract(v.totalVatAmount()),
                v.totalVatAmount(),
                BigDecimal.ZERO,
                v.totalAmount(),
                "CASH");
      }
    }
    return Optional.of(v.withLines(withLineNo(lines), invoice));
  }

  public ErpPage<ErpVoucher> listPayments(
      String companyId, LocalDate from, LocalDate toInclusive, int page, int size) {
    return list(
        "payment", PAYMENT_TYPE_IDS, PAYMENT_MAPPER, companyId, from, toInclusive, page, size);
  }

  public long countPayments(String companyId, LocalDate from, LocalDate toInclusive) {
    return count("payment", PAYMENT_TYPE_IDS, companyId, from, toInclusive);
  }

  public List<String> listPaymentIds(
      String companyId, LocalDate from, LocalDate toInclusive, long offset, int size) {
    return ids("payment", PAYMENT_TYPE_IDS, companyId, from, toInclusive, offset, size);
  }

  // ---------- dùng chung ----------

  private ErpPage<ErpVoucher> list(
      String name,
      List<Integer> types,
      RowMapper<ErpVoucher> mapper,
      String companyId,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    var params =
        period(companyId, from, to, types)
            .addValue("offset", (long) page * size)
            .addValue("size", size);
    long total =
        jdbc.queryForObject(
            sql.load(name + "-count"), period(companyId, from, to, types), Long.class);
    return new ErpPage<>(jdbc.query(sql.load(name + "-list"), params, mapper), page, size, total);
  }

  private long count(
      String name, List<Integer> types, String companyId, LocalDate from, LocalDate to) {
    return jdbc.queryForObject(
        sql.load(name + "-count"), period(companyId, from, to, types), Long.class);
  }

  private List<String> ids(
      String name,
      List<Integer> types,
      String companyId,
      LocalDate from,
      LocalDate to,
      long offset,
      int size) {
    var params =
        period(companyId, from, to, types).addValue("offset", offset).addValue("size", size);
    return jdbc.query(sql.load(name + "-ids"), params, (rs, i) -> rs.getString("ID"));
  }

  private static MapSqlParameterSource period(
      String companyId, LocalDate from, LocalDate toInclusive, List<Integer> types) {
    return new MapSqlParameterSource("companyId", companyId)
        .addValue("periodFrom", java.sql.Date.valueOf(from))
        .addValue("periodToExclusive", java.sql.Date.valueOf(toInclusive.plusDays(1)))
        .addValue("typeIds", types);
  }

  private static List<ErpJournalLine> withLineNo(List<ErpJournalLine> lines) {
    var out = new ArrayList<ErpJournalLine>();
    int i = 0;
    for (var l : lines) {
      out.add(
          new ErpJournalLine(
              ++i,
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
              l.vatAccount()));
    }
    return out;
  }

  /** "TM" → CASH, "CK" → BANK_TRANSFER, "TM/CK" → OTHER, trống → UNKNOWN. */
  public static String normalizePayment(String raw) {
    if (raw == null || raw.isBlank()) {
      return "UNKNOWN";
    }
    var s = raw.strip().toUpperCase();
    boolean cash = s.contains("TM") || s.contains("TIỀN MẶT");
    boolean bank = s.contains("CK") || s.contains("CHUYỂN KHOẢN");
    if (cash && bank) {
      return "OTHER";
    }
    return cash ? "CASH" : bank ? "BANK_TRANSFER" : "OTHER";
  }

  // ---------- RowMapper (public để test) ----------

  public static final RowMapper<ErpVoucher> SALES_MAPPER =
      (rs, i) -> {
        var date = ErpVoucherAdapter.date(rs, "Date");
        var total = ErpVoucherAdapter.money(rs, "TotalAmount");
        var vat = ErpVoucherAdapter.money(rs, "TotalVATAmount");
        var discount = ErpVoucherAdapter.money(rs, "TotalDiscountAmount");
        var invoice =
            new ErpInvoice(
                rs.getString("InvoiceTemplate"),
                rs.getString("InvoiceSeries"),
                rs.getString("InvoiceNo"),
                ErpVoucherAdapter.date(rs, "InvoiceDate"),
                null,
                null,
                total.subtract(vat).add(discount),
                vat,
                discount,
                total,
                normalizePayment(rs.getString("PaymentMethod")));
        return new ErpVoucher(
            rs.getString("ID") + "@" + date,
            rs.getString("CompanyID"),
            ErpEntityType.SALES_INVOICE,
            rs.getInt("TypeID"),
            rs.getString("NoFBook"),
            date,
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
            invoice);
      };

  public static final RowMapper<ErpJournalLine> SALES_LINE_MAPPER =
      (rs, i) ->
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

  public static final RowMapper<ErpVoucher> PAYMENT_MAPPER =
      (rs, i) ->
          new ErpVoucher(
              rs.getString("ID"),
              rs.getString("CompanyID"),
              ErpEntityType.PAYMENT,
              rs.getInt("TypeID"),
              rs.getString("NoFBook"),
              ErpVoucherAdapter.date(rs, "Date"),
              ErpVoucherAdapter.date(rs, "PostedDate"),
              rs.getString("Reason"),
              new ErpPartnerRef(
                  rs.getString("AccountingObjectID"),
                  rs.getString("PartnerCode"),
                  rs.getString("AccountingObjectName"),
                  rs.getString("TaxCode")),
              rs.getString("CurrencyID"),
              ErpVoucherAdapter.money(rs, "ExchangeRate"),
              ErpVoucherAdapter.money(rs, "TotalAmount"),
              ErpVoucherAdapter.money(rs, "TotalVATAmount"),
              BigDecimal.ZERO,
              rs.getBoolean("Recorded"),
              List.of(),
              null);

  public record PaymentLineRow(
      ErpJournalLine line,
      String invoiceTemplate,
      String invoiceSeries,
      String invoiceNo,
      LocalDate invoiceDate) {}

  public static class PaymentLineMapper implements RowMapper<PaymentLineRow> {
    @Override
    public PaymentLineRow mapRow(ResultSet rs, int i) throws SQLException {
      var line =
          new ErpJournalLine(
              i + 1,
              rs.getString("Description"),
              rs.getString("DebitAccount"),
              rs.getString("CreditAccount"),
              ErpVoucherAdapter.money(rs, "Amount"),
              ErpVoucherAdapter.money(rs, "AmountOriginal"),
              null,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              ErpVoucherAdapter.money(rs, "VATRate"),
              ErpVoucherAdapter.money(rs, "VATAmount"),
              rs.getString("VATAccount"));
      return new PaymentLineRow(
          line,
          rs.getString("InvoiceTemplate"),
          rs.getString("InvoiceSeries"),
          rs.getString("InvoiceNo"),
          ErpVoucherAdapter.date(rs, "InvoiceDate"));
    }
  }
}
