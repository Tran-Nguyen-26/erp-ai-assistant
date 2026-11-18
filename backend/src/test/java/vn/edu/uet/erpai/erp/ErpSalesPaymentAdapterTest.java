package vn.edu.uet.erpai.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.erp.adapter.ErpSalesPaymentAdapter;
import vn.edu.uet.erpai.erp.adapter.ErpVoucherAdapter;
import vn.edu.uet.erpai.erp.adapter.SqlLoader;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.service.ErpQueryService;

class ErpSalesPaymentAdapterTest {

  private static final String COMPANY = "11111111-1111-1111-1111-111111111111";
  private static final String ID = "22222222-2222-2222-2222-222222222222";

  private static ResultSet salesRow() throws Exception {
    var rs = mock(ResultSet.class);
    when(rs.getString("ID")).thenReturn(ID);
    when(rs.getString("CompanyID")).thenReturn(COMPANY);
    when(rs.getInt("TypeID")).thenReturn(321);
    when(rs.getString("NoFBook")).thenReturn("BH00077");
    when(rs.getTimestamp("Date")).thenReturn(Timestamp.valueOf("2026-10-20 00:00:00"));
    when(rs.getTimestamp("PostedDate")).thenReturn(Timestamp.valueOf("2026-10-20 00:00:00"));
    when(rs.getTimestamp("InvoiceDate")).thenReturn(Timestamp.valueOf("2026-10-20 00:00:00"));
    when(rs.getString("Reason")).thenReturn("Bán hàng");
    when(rs.getString("AccountingObjectID")).thenReturn("cust");
    when(rs.getString("AccountingObjectName")).thenReturn("Công ty Khách");
    when(rs.getString("PartnerCode")).thenReturn("KH001");
    when(rs.getString("PartnerTaxCode")).thenReturn("0109876543");
    when(rs.getString("InvoiceTemplate")).thenReturn("1");
    when(rs.getString("InvoiceSeries")).thenReturn("C26TBB");
    when(rs.getString("InvoiceNo")).thenReturn("0000999");
    when(rs.getString("PaymentMethod")).thenReturn("TM/CK");
    when(rs.getString("CurrencyID")).thenReturn("VND");
    when(rs.getBigDecimal("ExchangeRate")).thenReturn(BigDecimal.ONE);
    when(rs.getBigDecimal("TotalAmount")).thenReturn(new BigDecimal("11000000"));
    when(rs.getBigDecimal("TotalVATAmount")).thenReturn(new BigDecimal("1000000"));
    when(rs.getBoolean("Recorded")).thenReturn(true);
    return rs;
  }

  @Test
  void salesHeaderMapsToCanonicalWithDateInTheRefAndInvoiceFromHeader() throws Exception {
    var v = ErpSalesPaymentAdapter.SALES_MAPPER.mapRow(salesRow(), 0);

    assertThat(v.entityType()).isEqualTo(ErpEntityType.SALES_INVOICE);
    assertThat(v.ref())
        .isEqualTo(ID + "@2026-10-20"); // khóa kèm ngày: tra cứu nhanh theo PK (Date, ID)
    assertThat(v.voucherNo()).isEqualTo("BH00077");
    assertThat(v.partner().taxCode()).isEqualTo("0109876543");
    assertThat(v.invoice().invoiceNo()).isEqualTo("0000999");
    assertThat(v.invoice().amount())
        .isEqualByComparingTo("10000000"); // tổng − thuế + chiết khấu(0)
    assertThat(v.invoice().paymentMethod()).isEqualTo("OTHER"); // TM/CK
    assertThat(v.totalDiscountAmount()).isEqualByComparingTo("0"); // NULL → 0
  }

  @Test
  void paymentMethodTextIsNormalized() {
    assertThat(ErpSalesPaymentAdapter.normalizePayment("TM")).isEqualTo("CASH");
    assertThat(ErpSalesPaymentAdapter.normalizePayment("Tiền mặt")).isEqualTo("CASH");
    assertThat(ErpSalesPaymentAdapter.normalizePayment("CK")).isEqualTo("BANK_TRANSFER");
    assertThat(ErpSalesPaymentAdapter.normalizePayment("Chuyển khoản")).isEqualTo("BANK_TRANSFER");
    assertThat(ErpSalesPaymentAdapter.normalizePayment("TM/CK")).isEqualTo("OTHER");
    assertThat(ErpSalesPaymentAdapter.normalizePayment(null)).isEqualTo("UNKNOWN");
    assertThat(ErpSalesPaymentAdapter.normalizePayment("  ")).isEqualTo("UNKNOWN");
  }

  @Test
  @SuppressWarnings("unchecked")
  void salesLookupWithDateUsesDateIndexAndFallsBackToAnyPartitionWhenNoLines() throws Exception {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var header = ErpSalesPaymentAdapter.SALES_MAPPER.mapRow(salesRow(), 0);
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of(header)) // header
        .thenReturn(List.of()) // dòng theo phân vùng: rỗng
        .thenReturn(List.of()); // dòng dự phòng
    var adapter = new ErpSalesPaymentAdapter(jdbc, new SqlLoader());

    var v =
        adapter
            .findSalesInvoice(COMPANY, ID, Optional.of(LocalDate.of(2026, 10, 20)))
            .orElseThrow();

    assertThat(v.lines()).isEmpty();
    var sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, times(3))
        .query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
    assertThat(sql.getAllValues().get(0)).contains("h.[Date] >= :dateFrom");
    assertThat(sql.getAllValues().get(1)).contains("AND d.DatePartition = :partitionDate");
    assertThat(sql.getAllValues().get(2)).doesNotContain("AND d.DatePartition");
  }

  @Test
  @SuppressWarnings("unchecked")
  void salesLookupWithoutDateFiltersOnlyByCompanyAndId() throws Exception {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of());
    new ErpSalesPaymentAdapter(jdbc, new SqlLoader())
        .findSalesInvoice(COMPANY, ID, Optional.empty());
    var sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
    assertThat(sql.getValue()).doesNotContain(":dateFrom").contains("h.ID = :id");
  }

  @Test
  void queryServiceAcceptsGuidWithOptionalDateAndRejectsBadRefs() {
    var salesPayment = mock(ErpSalesPaymentAdapter.class);
    var service =
        new ErpQueryService(
            mock(ErpVoucherAdapter.class),
            salesPayment,
            mock(vn.edu.uet.erpai.erp.adapter.ErpVoucherKindAdapter.class));
    when(salesPayment.findSalesInvoice(anyString(), anyString(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.getVoucher(ErpEntityType.SALES_INVOICE, COMPANY, ID + "@2026-10-20"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getCode()).isEqualTo("ERP_VOUCHER_NOT_FOUND"));
    verify(salesPayment).findSalesInvoice(COMPANY, ID, Optional.of(LocalDate.of(2026, 10, 20)));

    for (var bad :
        new String[] {"không-phải-guid", ID + "@20-10-2026", "'; DROP TABLE x;--@2026-10-20"}) {
      assertThatThrownBy(() -> service.getVoucher(ErpEntityType.SALES_INVOICE, COMPANY, bad))
          .isInstanceOfSatisfying(
              BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("ERP_INVALID_REF"));
    }
    assertThatThrownBy(() -> service.getVoucher(ErpEntityType.JOURNAL_ENTRY, COMPANY, ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getCode()).isEqualTo("RULE_ENTITY_TYPE_UNSUPPORTED"));
  }

  @Test
  void supportedTypesAreExactlyTheOnesWithAdapters() {
    assertThat(ErpQueryService.SUPPORTED_TYPES)
        .containsExactlyInAnyOrder(
            ErpEntityType.PURCHASE_INVOICE,
            ErpEntityType.SALES_INVOICE,
            ErpEntityType.PAYMENT,
            ErpEntityType.RECEIPT,
            ErpEntityType.BANK_DEBIT,
            ErpEntityType.BANK_CREDIT,
            ErpEntityType.PURCHASE_RETURN,
            ErpEntityType.SALES_RETURN);
  }
}
