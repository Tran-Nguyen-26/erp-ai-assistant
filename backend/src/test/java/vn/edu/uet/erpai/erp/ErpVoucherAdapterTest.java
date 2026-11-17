package vn.edu.uet.erpai.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import vn.edu.uet.erpai.erp.adapter.ErpVoucherAdapter;
import vn.edu.uet.erpai.erp.adapter.SqlLoader;
import vn.edu.uet.erpai.erp.model.ErpEntityType;

class ErpVoucherAdapterTest {

  private static final String COMPANY = "11111111-1111-1111-1111-111111111111";
  private static final String ID = "22222222-2222-2222-2222-222222222222";

  private static ResultSet headerRow() throws Exception {
    var rs = mock(ResultSet.class);
    when(rs.getString("ID")).thenReturn(ID);
    when(rs.getString("CompanyID")).thenReturn(COMPANY);
    when(rs.getInt("TypeID")).thenReturn(210);
    when(rs.getString("NoFBook")).thenReturn("MH00123");
    when(rs.getTimestamp("Date")).thenReturn(Timestamp.valueOf("2026-10-15 00:00:00"));
    when(rs.getTimestamp("PostedDate")).thenReturn(Timestamp.valueOf("2026-10-15 00:00:00"));
    when(rs.getString("Reason")).thenReturn("Mua bàn phím");
    when(rs.getString("AccountingObjectID")).thenReturn("obj");
    when(rs.getString("PartnerCode")).thenReturn("NCC001");
    when(rs.getString("AccountingObjectName")).thenReturn("Công ty A");
    when(rs.getString("PartnerTaxCode")).thenReturn("0101234567");
    when(rs.getString("CurrencyID")).thenReturn("VND");
    when(rs.getBigDecimal("ExchangeRate")).thenReturn(BigDecimal.ONE);
    when(rs.getBigDecimal("TotalAmount")).thenReturn(new BigDecimal("11000000"));
    when(rs.getBigDecimal("TotalVATAmount")).thenReturn(new BigDecimal("1000000"));
    when(rs.getBigDecimal("TotalDiscountAmount")).thenReturn(null); // NULL -> 0
    when(rs.getBoolean("Recorded")).thenReturn(true);
    return rs;
  }

  @Test
  void headerMapperMapsToCanonicalModelAndDefaultsNullMoneyToZero() throws Exception {
    var v = ErpVoucherAdapter.PURCHASE_HEADER_MAPPER.mapRow(headerRow(), 0);
    assertThat(v.entityType()).isEqualTo(ErpEntityType.PURCHASE_INVOICE);
    assertThat(v.voucherNo()).isEqualTo("MH00123");
    assertThat(v.voucherDate()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(v.partner().taxCode()).isEqualTo("0101234567");
    assertThat(v.totalAmount()).isEqualByComparingTo("11000000");
    assertThat(v.totalDiscountAmount()).isEqualByComparingTo("0");
    assertThat(v.isRecorded()).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void findPurchaseInvoiceAssemblesLinesAndInvoiceFromDetailRows() throws Exception {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var header = ErpVoucherAdapter.PURCHASE_HEADER_MAPPER.mapRow(headerRow(), 0);
    var lineRs = mock(ResultSet.class);
    when(lineRs.getString("Description")).thenReturn("Bàn phím");
    when(lineRs.getString("DebitAccount")).thenReturn("1561");
    when(lineRs.getString("CreditAccount")).thenReturn("331");
    when(lineRs.getBigDecimal("Amount")).thenReturn(new BigDecimal("10000000"));
    when(lineRs.getBigDecimal("VATRate")).thenReturn(new BigDecimal("0.1"));
    when(lineRs.getBigDecimal("VATAmount")).thenReturn(new BigDecimal("1000000"));
    when(lineRs.getString("InvoiceNo")).thenReturn("0000123");
    when(lineRs.getString("InvoiceSeries")).thenReturn("C26TAA");
    var line = new ErpVoucherAdapter.LineRowMapper().mapRow(lineRs, 0);

    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of(header))
        .thenReturn(List.of(line));

    var adapter = new ErpVoucherAdapter(jdbc, new SqlLoader());
    var result = adapter.findPurchaseInvoice(COMPANY, ID).orElseThrow();

    assertThat(result.lines()).hasSize(1);
    assertThat(result.lines().get(0).debitAccount()).isEqualTo("1561");
    assertThat(result.invoice().invoiceNo()).isEqualTo("0000123");
    assertThat(result.invoice().sellerTaxCode()).isEqualTo("0101234567");
    assertThat(result.invoice().amount()).isEqualByComparingTo("10000000");
  }

  @Test
  @SuppressWarnings("unchecked")
  void findPurchaseInvoiceReturnsEmptyWhenHeaderMissing() {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of());
    var adapter = new ErpVoucherAdapter(jdbc, new SqlLoader());
    assertThat(adapter.findPurchaseInvoice(COMPANY, ID)).isEmpty();
  }
}
