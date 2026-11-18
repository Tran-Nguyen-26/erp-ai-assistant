package vn.edu.uet.erpai.erp.adapter;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.erp.model.ErpAccount;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpInvoice;
import vn.edu.uet.erpai.erp.model.ErpJournalLine;
import vn.edu.uet.erpai.erp.model.ErpPage;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.model.VoucherFilter;

/**
 * Adapter SQL Server → canonical model. ĐÂY là nơi duy nhất biết tên cột ERP. Chỉ SELECT; phải được
 * gọi trong transaction readOnly của {@code erpTransactionManager}.
 */
@Component
public class ErpVoucherAdapter {

  /** TypeID hóa đơn mua hàng/dịch vụ chưa & đã thanh toán (xem docs/erp-mapping.md §5). */
  public static final List<Integer> PURCHASE_TYPE_IDS =
      List.of(210, 211, 212, 213, 214, 215, 240, 241, 242, 243, 244, 245);

  private final NamedParameterJdbcTemplate jdbc;
  private final SqlLoader sql;

  public ErpVoucherAdapter(NamedParameterJdbcTemplate erpJdbcTemplate, SqlLoader sql) {
    this.jdbc = erpJdbcTemplate;
    this.sql = sql;
  }

  public Optional<ErpVoucher> findPurchaseInvoice(String companyId, String id) {
    var params = new MapSqlParameterSource("companyId", companyId).addValue("id", id);
    var header =
        jdbc.query(sql.load("purchase-invoice-by-id"), params, PURCHASE_HEADER_MAPPER).stream()
            .findFirst();
    if (header.isEmpty()) {
      return Optional.empty();
    }
    var rows = jdbc.query(sql.load("purchase-invoice-lines"), params, new LineRowMapper());
    var lines = new ArrayList<ErpJournalLine>();
    ErpInvoice invoice = null;
    var v = header.get();
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
                v.totalAmount().subtract(v.totalVatAmount()).add(v.totalDiscountAmount()),
                v.totalVatAmount(),
                v.totalDiscountAmount(),
                v.totalAmount(),
                paymentMethodOf(v.voucherTypeId()));
      }
    }
    return Optional.of(v.withLines(lines, invoice));
  }

  /** Suy phương thức thanh toán từ TypeID chứng từ mua hàng/dịch vụ (docs/erp-mapping.md §5). */
  public static String paymentMethodOf(int typeId) {
    return switch (typeId) {
      case 211, 241, 215, 245 -> "CASH";
      case 212, 242, 213, 243 -> "BANK_TRANSFER";
      case 214, 244 -> "CARD";
      default -> "UNKNOWN"; // 210/240: mua chịu, ghi công nợ
    };
  }

  public ErpPage<ErpVoucher> listPurchaseInvoices(
      String companyId, LocalDate from, LocalDate toInclusive, int page, int size) {
    return listPurchaseInvoices(companyId, from, toInclusive, page, size, VoucherFilter.NONE);
  }

  public ErpPage<ErpVoucher> listPurchaseInvoices(
      String companyId,
      LocalDate from,
      LocalDate toInclusive,
      int page,
      int size,
      VoucherFilter filter) {
    var params =
        filter
            .applyTo(periodParams(companyId, from, toInclusive))
            .addValue("offset", (long) page * size)
            .addValue("size", size);
    long total = jdbc.queryForObject(sql.load("purchase-invoice-count"), params, Long.class);
    var items = jdbc.query(sql.load("purchase-invoice-list"), params, PURCHASE_HEADER_MAPPER);
    return new ErpPage<>(items, page, size, total);
  }

  public long countPurchaseInvoices(String companyId, LocalDate from, LocalDate toInclusive) {
    return jdbc.queryForObject(
        sql.load("purchase-invoice-count"),
        VoucherFilter.NONE.applyTo(periodParams(companyId, from, toInclusive)),
        Long.class);
  }

  public List<String> listPurchaseInvoiceIds(
      String companyId, LocalDate from, LocalDate toInclusive, long offset, int size) {
    var params =
        periodParams(companyId, from, toInclusive)
            .addValue("offset", offset)
            .addValue("size", size);
    return jdbc.query(sql.load("purchase-invoice-ids"), params, (rs, i) -> rs.getString("ID"));
  }

  private static MapSqlParameterSource periodParams(
      String companyId, LocalDate from, LocalDate toInclusive) {
    return new MapSqlParameterSource("companyId", companyId)
        .addValue("periodFrom", java.sql.Date.valueOf(from))
        .addValue("periodToExclusive", java.sql.Date.valueOf(toInclusive.plusDays(1)))
        .addValue("typeIds", PURCHASE_TYPE_IDS);
  }

  /**
   * Chạy một truy vấn rule liên chứng từ đã được nạp từ file (sqlName đã kiểm tra ở tầng service).
   * Tham số entityRef rỗng được thay bằng GUID rỗng để so sánh {@code <>} không lỗi.
   */
  public List<java.util.Map<String, Object>> queryRule(
      String sqlName, java.util.Map<String, Object> params) {
    var p = new MapSqlParameterSource();
    params.forEach(p::addValue);
    if (params.containsKey("entityRef") && params.get("entityRef") == null) {
      p.addValue("entityRef", "00000000-0000-0000-0000-000000000000");
    }
    return jdbc.queryForList(sql.load(sqlName), p);
  }

  public Optional<ErpPartnerRef> findPartnerByTaxCode(String companyId, String taxCode) {
    var params = new MapSqlParameterSource("companyId", companyId).addValue("taxCode", taxCode);
    return jdbc
        .query(
            sql.load("partner-by-tax-code"),
            params,
            (rs, i) ->
                new ErpPartnerRef(
                    rs.getString("ID"),
                    rs.getString("AccountingObjectCode"),
                    rs.getString("AccountingObjectName"),
                    rs.getString("TaxCode")))
        .stream()
        .findFirst();
  }

  public List<ErpAccount> listAccounts(String companyId, int page, int size) {
    var params =
        new MapSqlParameterSource("companyId", companyId)
            .addValue("offset", (long) page * size)
            .addValue("size", size);
    return jdbc.query(
        sql.load("accounts-list"),
        params,
        (rs, i) ->
            new ErpAccount(
                rs.getString("AccountNumber"),
                rs.getString("AccountName"),
                !rs.getBoolean("IsParentNode"),
                rs.getBoolean("IsActive")));
  }

  // ---------- RowMapper (public để test bằng ResultSet giả) ----------

  public static final RowMapper<ErpVoucher> PURCHASE_HEADER_MAPPER =
      (rs, i) ->
          new ErpVoucher(
              rs.getString("ID"),
              rs.getString("CompanyID"),
              ErpEntityType.PURCHASE_INVOICE,
              rs.getInt("TypeID"),
              rs.getString("NoFBook"),
              date(rs, "Date"),
              date(rs, "PostedDate"),
              rs.getString("Reason"),
              new ErpPartnerRef(
                  rs.getString("AccountingObjectID"),
                  rs.getString("PartnerCode"),
                  rs.getString("AccountingObjectName"),
                  rs.getString("PartnerTaxCode")),
              rs.getString("CurrencyID"),
              money(rs, "ExchangeRate"),
              money(rs, "TotalAmount"),
              money(rs, "TotalVATAmount"),
              money(rs, "TotalDiscountAmount"),
              rs.getBoolean("Recorded"),
              List.of(),
              null);

  public record LineRow(
      ErpJournalLine line,
      String invoiceTemplate,
      String invoiceSeries,
      String invoiceNo,
      LocalDate invoiceDate) {}

  public static class LineRowMapper implements RowMapper<LineRow> {
    @Override
    public LineRow mapRow(ResultSet rs, int rowNum) throws SQLException {
      var line =
          new ErpJournalLine(
              rowNum + 1,
              rs.getString("Description"),
              rs.getString("DebitAccount"),
              rs.getString("CreditAccount"),
              money(rs, "Amount"),
              money(rs, "AmountOriginal"),
              rs.getString("MaterialGoodsID"),
              money(rs, "Quantity"),
              money(rs, "UnitPrice"),
              money(rs, "VATRate"),
              money(rs, "VATAmount"),
              rs.getString("VATAccount"));
      return new LineRow(
          line,
          rs.getString("InvoiceTemplate"),
          rs.getString("InvoiceSeries"),
          rs.getString("InvoiceNo"),
          date(rs, "InvoiceDate"));
    }
  }

  static LocalDate date(ResultSet rs, String col) throws SQLException {
    var d = rs.getTimestamp(col);
    return d == null ? null : d.toLocalDateTime().toLocalDate();
  }

  /** Tiền/số lượng: NULL → 0 để rule không phải xử lý null. */
  static BigDecimal money(ResultSet rs, String col) throws SQLException {
    var v = rs.getBigDecimal(col);
    return v == null ? BigDecimal.ZERO : v;
  }
}
