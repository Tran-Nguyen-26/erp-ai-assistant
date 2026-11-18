package vn.edu.uet.erpai.erp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.erp.adapter.ErpVoucherAdapter;
import vn.edu.uet.erpai.erp.service.ErpQueryService;

class ErpQueryServiceTest {

  private static final String COMPANY = "11111111-1111-1111-1111-111111111111";
  private final ErpVoucherAdapter adapter = mock(ErpVoucherAdapter.class);
  private final ErpQueryService service =
      new ErpQueryService(adapter, mock(vn.edu.uet.erpai.erp.adapter.ErpSalesPaymentAdapter.class));

  @Test
  void rejectsNonGuidRefBeforeTouchingDatabase() {
    assertThatThrownBy(() -> service.getPurchaseInvoice(COMPANY, "1; DROP TABLE x"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                org.assertj.core.api.Assertions.assertThat(e.getCode())
                    .isEqualTo("ERP_INVALID_REF"));
    verifyNoInteractions(adapter);
  }

  @Test
  void rejectsPeriodLongerThanTwelveMonths() {
    assertThatThrownBy(
            () ->
                service.listPurchaseInvoices(
                    COMPANY, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 2, 1), 0, 20))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                org.assertj.core.api.Assertions.assertThat(e.getCode())
                    .isEqualTo("ERP_PERIOD_TOO_LONG"));
    verifyNoInteractions(adapter);
  }

  @Test
  void rejectsInvertedPeriod() {
    assertThatThrownBy(
            () ->
                service.listPurchaseInvoices(
                    COMPANY, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1), 0, 20))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void infrastructureFailuresBecomeErpUnavailableAndTimeoutCodes() {
    when(adapter.findPurchaseInvoice(any(), any()))
        .thenThrow(new org.springframework.jdbc.CannotGetJdbcConnectionException("down"));
    assertThatThrownBy(() -> service.getPurchaseInvoice(COMPANY, COMPANY))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo("ERP_UNAVAILABLE");
              org.assertj.core.api.Assertions.assertThat(e.getStatus().value()).isEqualTo(502);
            });

    when(adapter.countPurchaseInvoices(any(), any(), any()))
        .thenThrow(new org.springframework.dao.QueryTimeoutException("slow"));
    assertThatThrownBy(
            () ->
                service.countPurchaseInvoices(
                    COMPANY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                org.assertj.core.api.Assertions.assertThat(e.getCode())
                    .isEqualTo("ERP_QUERY_TIMEOUT"));
  }

  @Test
  void missingVoucherIsNotFound() {
    when(adapter.findPurchaseInvoice(any(), any())).thenReturn(java.util.Optional.empty());
    assertThatThrownBy(() -> service.getPurchaseInvoice(COMPANY, COMPANY))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                org.assertj.core.api.Assertions.assertThat(e.getCode())
                    .isEqualTo("ERP_VOUCHER_NOT_FOUND"));
  }
}
