package vn.edu.uet.erpai.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpJournalLine;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.review.service.VoucherPatcher;

class VoucherPatcherTest {

  private final VoucherPatcher patcher = new VoucherPatcher();

  private final ErpVoucher voucher =
      new ErpVoucher(
          "ref",
          "c",
          ErpEntityType.PURCHASE_INVOICE,
          210,
          "MH1",
          LocalDate.of(2026, 10, 15),
          null,
          "d",
          new ErpPartnerRef("p", "N", "Alpha", "12345"),
          "VND",
          BigDecimal.ONE,
          new BigDecimal("11000000"),
          new BigDecimal("1000000"),
          BigDecimal.ZERO,
          true,
          List.of(
              new ErpJournalLine(
                  1,
                  "a",
                  "1561",
                  "331",
                  new BigDecimal("10000000"),
                  null,
                  null,
                  BigDecimal.ONE,
                  new BigDecimal("10000000"),
                  BigDecimal.TEN,
                  new BigDecimal("1050000"),
                  "1331")),
          null);

  @Test
  void appliesChangeToACopyAndLeavesOriginalUntouched() {
    var patched = patcher.apply(voucher, "lines[0].vatAmount", "1000000.00");

    assertThat(patched.lines().get(0).vatAmount()).isEqualByComparingTo("1000000");
    assertThat(voucher.lines().get(0).vatAmount()).isEqualByComparingTo("1050000");
    assertThat(patched.lines().get(0).debitAccount()).isEqualTo("1561"); // trường khác giữ nguyên
  }

  @Test
  void readsAndPatchesVoucherAndPartnerFields() {
    assertThat(patcher.read(voucher, "partner.taxCode")).isEqualTo("12345");
    assertThat(patcher.read(voucher, "voucherDate")).isEqualTo("2026-10-15");
    assertThat(patcher.apply(voucher, "partner.taxCode", "0101234567").partner().taxCode())
        .isEqualTo("0101234567");
    assertThat(patcher.apply(voucher, "voucherDate", "2026-10-20").voucherDate())
        .isEqualTo(LocalDate.of(2026, 10, 20));
    assertThat(patcher.apply(voucher, "totalAmount", "5").totalAmount()).isEqualByComparingTo("5");
  }

  @Test
  void rejectsUnknownOutOfRangeAndMalformedPaths() {
    for (var bad :
        List.of(
            "lines[3].amount",
            "lines[0].materialGoodsId",
            "ref",
            "companyId",
            "lines[0]",
            "invoice.invoiceNo",
            "../x")) {
      assertThatThrownBy(() -> patcher.apply(voucher, bad, "1"))
          .as(bad)
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getCode()).isEqualTo("PROPOSAL_PATH_INVALID"));
    }
  }

  @Test
  void rejectsNonNumericAndNonDateValues() {
    assertThatThrownBy(() -> patcher.apply(voucher, "lines[0].amount", "abc"))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> patcher.apply(voucher, "voucherDate", "15/10/2026"))
        .isInstanceOf(BusinessException.class);
  }
}
