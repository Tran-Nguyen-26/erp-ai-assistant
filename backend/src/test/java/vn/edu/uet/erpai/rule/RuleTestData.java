package vn.edu.uet.erpai.rule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpJournalLine;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;

final class RuleTestData {

  private RuleTestData() {}

  static ErpVoucher voucher(String voucherNo, String taxCode, List<ErpJournalLine> lines) {
    return new ErpVoucher(
        "22222222-2222-2222-2222-222222222222",
        "11111111-1111-1111-1111-111111111111",
        ErpEntityType.PURCHASE_INVOICE,
        210,
        voucherNo,
        LocalDate.of(2026, 10, 15),
        LocalDate.of(2026, 10, 15),
        "Mua hàng",
        new ErpPartnerRef("p1", "NCC001", "Công ty A", taxCode),
        "VND",
        BigDecimal.ONE,
        new BigDecimal("22000000"),
        new BigDecimal("2000000"),
        BigDecimal.ZERO,
        true,
        lines,
        null);
  }

  static ErpJournalLine line(int no, String amount, String vatRate, String vatAmount) {
    return new ErpJournalLine(
        no,
        "Hàng " + no,
        "1561",
        "331",
        new BigDecimal(amount),
        new BigDecimal(amount),
        null,
        BigDecimal.ONE,
        new BigDecimal(amount),
        new BigDecimal(vatRate),
        new BigDecimal(vatAmount),
        "1331");
  }
}
