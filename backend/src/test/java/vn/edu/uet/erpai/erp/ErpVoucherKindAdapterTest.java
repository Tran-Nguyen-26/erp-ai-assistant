package vn.edu.uet.erpai.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import vn.edu.uet.erpai.erp.adapter.ErpVoucherKindAdapter;
import vn.edu.uet.erpai.erp.adapter.ErpVoucherKindAdapter.Kind;
import vn.edu.uet.erpai.erp.adapter.SqlLoader;
import vn.edu.uet.erpai.erp.model.ErpEntityType;

class ErpVoucherKindAdapterTest {

  private static final String COMPANY = "11111111-1111-1111-1111-111111111111";
  private static final String ID = "22222222-2222-2222-2222-222222222222";

  private static final Set<String> HEADER_ALIASES =
      Set.of(
          "ID",
          "CompanyID",
          "TypeID",
          "Date",
          "PostedDate",
          "NoFBook",
          "Reason",
          "CurrencyID",
          "ExchangeRate",
          "TotalAmount",
          "TotalVATAmount",
          "TotalDiscountAmount",
          "Recorded",
          "AccountingObjectID",
          "AccountingObjectName",
          "PartnerTaxCode",
          "PartnerCode",
          "InvoiceTemplate",
          "InvoiceSeries",
          "InvoiceNo",
          "InvoiceDate",
          "PaymentMethod");
  private static final Set<String> LINE_ALIASES =
      Set.of(
          "OrderPriority",
          "Description",
          "DebitAccount",
          "CreditAccount",
          "Amount",
          "AmountOriginal",
          "MaterialGoodsID",
          "Quantity",
          "UnitPrice",
          "VATRate",
          "VATAmount",
          "VATAccount",
          "InvoiceTemplate",
          "InvoiceSeries",
          "InvoiceNo",
          "InvoiceDate");

  // ---------- SQL ↔ RowMapper: mọi loại phải trả về đúng bộ alias cột ----------

  private static String sql(String name) throws IOException {
    var raw =
        new String(
            new ClassPathResource("sql/erp/" + name + ".sql").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    return raw.replaceAll("--[^\n]*", "");
  }

  /**
   * Tách danh sách cột của câu SELECT đầu tiên (theo dấu phẩy ngoài ngoặc) rồi lấy alias/tên cột.
   */
  private static Set<String> selectedColumns(String sql) {
    int start = sql.toUpperCase().indexOf("SELECT") + 6;
    int end = sql.toUpperCase().indexOf("\nFROM", start);
    var list = sql.substring(start, end);
    var cols = new java.util.HashSet<String>();
    var cur = new StringBuilder();
    int depth = 0;
    for (char c : list.toCharArray()) {
      if (c == '(') depth++;
      if (c == ')') depth--;
      if (c == ',' && depth == 0) {
        cols.add(alias(cur.toString()));
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    cols.add(alias(cur.toString()));
    return cols;
  }

  private static String alias(String expr) {
    var e = expr.strip().replaceAll("\\s+", " ");
    var m = java.util.regex.Pattern.compile("(?i) AS (\\w+)$").matcher(e);
    if (m.find()) return m.group(1);
    return e.substring(e.lastIndexOf('.') + 1).replace("[", "").replace("]", "");
  }

  @Test
  void everyKindsSqlFilesExposeTheSameColumnAliasesTheMapperReads() throws Exception {
    for (var kind : Kind.values()) {
      var prefix = kind.name().toLowerCase().replace('_', '-');
      for (var file : List.of("by-id", "list")) {
        assertThat(selectedColumns(sql(prefix + "-" + file)))
            .as(prefix + "-" + file)
            .isEqualTo(HEADER_ALIASES);
      }
      assertThat(selectedColumns(sql(prefix + "-lines")))
          .as(prefix + "-lines")
          .isEqualTo(LINE_ALIASES);
      assertThat(selectedColumns(sql(prefix + "-count")))
          .as(prefix + "-count")
          .containsExactly("Total");
      assertThat(selectedColumns(sql(prefix + "-ids"))).as(prefix + "-ids").containsExactly("ID");
      // truy vấn danh sách luôn kèm CompanyID, kỳ và phân trang
      var list = sql(prefix + "-list");
      assertThat(list)
          .contains(
              ":companyId",
              ":periodFrom",
              ":periodToExclusive",
              "OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY");
    }
  }

  @Test
  void typeIdRangesMatchTheVoucherTypeCatalogue() {
    assertThat(Kind.PAYMENT.typeIds()).containsExactlyElementsOf(range(110, 118));
    assertThat(Kind.RECEIPT.typeIds()).containsExactlyElementsOf(range(100, 104));
    assertThat(Kind.BANK_CREDIT.typeIds()).containsExactlyElementsOf(range(160, 164));
    assertThat(Kind.BANK_DEBIT.typeIds())
        .contains(120, 128, 130, 135, 140, 145)
        .doesNotContain(129, 136, 150);
    assertThat(Kind.PURCHASE_RETURN.typeIds()).containsExactly(220, 221, 222, 230, 231, 232);
    assertThat(Kind.SALES_RETURN.typeIds()).containsExactlyElementsOf(range(330, 335));
    assertThat(Kind.of(ErpEntityType.PURCHASE_INVOICE)).isEmpty();
    assertThat(Kind.of(ErpEntityType.RECEIPT)).contains(Kind.RECEIPT);
  }

  private static List<Integer> range(int a, int b) {
    var out = new ArrayList<Integer>();
    for (int i = a; i <= b; i++) out.add(i);
    return out;
  }

  // ---------- RowMapper và lắp ráp chứng từ ----------

  private static ResultSet header(int typeId, String invoiceNo, String paymentMethod)
      throws Exception {
    var rs = mock(ResultSet.class);
    when(rs.getString("ID")).thenReturn(ID);
    when(rs.getString("CompanyID")).thenReturn(COMPANY);
    when(rs.getInt("TypeID")).thenReturn(typeId);
    when(rs.getString("NoFBook")).thenReturn("PC001");
    when(rs.getTimestamp("Date")).thenReturn(Timestamp.valueOf("2026-10-20 00:00:00"));
    when(rs.getTimestamp("PostedDate")).thenReturn(Timestamp.valueOf("2026-10-20 00:00:00"));
    when(rs.getString("AccountingObjectID")).thenReturn("obj");
    when(rs.getString("AccountingObjectName")).thenReturn("Công ty Alpha");
    when(rs.getString("PartnerCode")).thenReturn("NCC1");
    when(rs.getString("PartnerTaxCode")).thenReturn("0101234567");
    when(rs.getString("CurrencyID")).thenReturn("VND");
    when(rs.getBigDecimal("ExchangeRate")).thenReturn(BigDecimal.ONE);
    when(rs.getBigDecimal("TotalAmount")).thenReturn(new BigDecimal("11000000"));
    when(rs.getBigDecimal("TotalVATAmount")).thenReturn(new BigDecimal("1000000"));
    when(rs.getBigDecimal("TotalDiscountAmount")).thenReturn(null); // NULL → 0
    when(rs.getBoolean("Recorded")).thenReturn(true);
    when(rs.getString("InvoiceNo")).thenReturn(invoiceNo);
    when(rs.getString("InvoiceSeries")).thenReturn("C26TAA");
    when(rs.getString("PaymentMethod")).thenReturn(paymentMethod);
    return rs;
  }

  @Test
  @SuppressWarnings("unchecked")
  void paymentTakesItsInvoiceFromALineAndPartnerIsTheSeller() throws Exception {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var h = ErpVoucherKindAdapter.headerMapper(Kind.PAYMENT).mapRow(header(111, null, null), 0);
    var lineRs = mock(ResultSet.class);
    when(lineRs.getString("Description")).thenReturn("Trả tiền hàng");
    when(lineRs.getString("DebitAccount")).thenReturn("331");
    when(lineRs.getString("CreditAccount")).thenReturn("1111");
    when(lineRs.getBigDecimal("Amount")).thenReturn(new BigDecimal("11000000"));
    when(lineRs.getString("InvoiceNo")).thenReturn("0000555");
    when(lineRs.getString("InvoiceSeries")).thenReturn("C26TAA");
    var line = ErpVoucherKindAdapter.LINE_MAPPER.mapRow(lineRs, 0);
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of(h))
        .thenReturn(List.of(line));

    var v =
        new ErpVoucherKindAdapter(jdbc, new SqlLoader())
            .find(Kind.PAYMENT, COMPANY, ID)
            .orElseThrow();

    assertThat(v.entityType()).isEqualTo(ErpEntityType.PAYMENT);
    assertThat(v.ref()).isEqualTo(ID);
    assertThat(v.lines()).hasSize(1);
    assertThat(v.lines().get(0).quantity()).isEqualByComparingTo("0"); // cột không có → NULL → 0
    assertThat(v.invoice().invoiceNo()).isEqualTo("0000555");
    assertThat(v.invoice().sellerTaxCode())
        .isEqualTo("0101234567"); // chi trả NCC: đối tượng là người bán
    assertThat(v.invoice().paymentMethod()).isEqualTo("CASH");
  }

  @Test
  void returnsCarryTheInvoiceInTheHeaderAndSalesReturnsHaveNoSellerTaxCode() throws Exception {
    var purchase =
        ErpVoucherKindAdapter.headerMapper(Kind.PURCHASE_RETURN)
            .mapRow(header(220, "0000777", "CK"), 0);
    assertThat(purchase.invoice().invoiceNo()).isEqualTo("0000777");
    assertThat(purchase.invoice().sellerTaxCode()).isEqualTo("0101234567");
    assertThat(purchase.invoice().paymentMethod()).isEqualTo("BANK_TRANSFER");
    assertThat(purchase.voucher().totalDiscountAmount()).isEqualByComparingTo("0");

    var sales =
        ErpVoucherKindAdapter.headerMapper(Kind.SALES_RETURN)
            .mapRow(header(330, "0000888", "TM"), 0);
    assertThat(sales.invoice().sellerTaxCode())
        .isNull(); // đối tượng là khách hàng, không phải người bán
    assertThat(sales.invoice().paymentMethod()).isEqualTo("CASH");
    assertThat(sales.voucher().entityType()).isEqualTo(ErpEntityType.SALES_RETURN);

    // chứng từ không có thông tin hóa đơn ở header (phiếu thu) → invoice null
    assertThat(
            ErpVoucherKindAdapter.headerMapper(Kind.RECEIPT)
                .mapRow(header(101, null, null), 0)
                .invoice())
        .isNull();
  }

  @Test
  void bankDebitPaymentMethodDependsOnTheVoucherType() throws Exception {
    var transfer =
        ErpVoucherKindAdapter.headerMapper(Kind.BANK_DEBIT).mapRow(header(121, "1", null), 0);
    var cashCheque =
        ErpVoucherKindAdapter.headerMapper(Kind.BANK_DEBIT).mapRow(header(141, "1", null), 0);
    assertThat(transfer.invoice().paymentMethod()).isEqualTo("BANK_TRANSFER"); // ủy nhiệm chi
    assertThat(cashCheque.invoice().paymentMethod()).isEqualTo("CASH"); // séc tiền mặt
  }
}
