package vn.edu.uet.erpai.eval;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpJournalLine;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;

/**
 * Sinh chứng từ TỔNG HỢP (không dùng dữ liệu công ty) cài lỗi có nhãn, xác định theo seed. Chỉ cài
 * các lỗi mà rule SpEL phát hiện được không cần dữ liệu tham chiếu ERP.
 */
public final class SyntheticVoucherFactory {

  /** Loại lỗi cài vào → ruleId kỳ vọng. */
  public enum Fault {
    BAD_TAX_CODE("FLD_TAX_CODE_FORMAT"),
    BLANK_VOUCHER_NO("FLD_REQUIRED_FIELDS"),
    FUTURE_DATE("FLD_DATE_IN_FUTURE");

    final String ruleId;

    Fault(String ruleId) {
      this.ruleId = ruleId;
    }
  }

  private static final String COMPANY = "00000000-0000-0000-0000-00000000e001";
  private static final String[] BAD_TAX_CODES = {
    "12345", "ABCDEFGHIJ", "01012345678901", "0101-234567", "0101 234 567"
  };

  private final Random random;

  public SyntheticVoucherFactory(long seed) {
    this.random = new Random(seed);
  }

  /**
   * @param faultRate xác suất một chứng từ bị cài lỗi (0..1); lỗi chọn ngẫu nhiên, có thể chồng 2
   *     lỗi.
   */
  public List<EvalCase> generate(int count, double faultRate) {
    var out = new ArrayList<EvalCase>(count);
    for (int i = 0; i < count; i++) {
      var set = java.util.EnumSet.noneOf(Fault.class);
      if (random.nextDouble() < faultRate) {
        set.add(Fault.values()[random.nextInt(Fault.values().length)]);
        if (random.nextDouble() < 0.2) {
          set.add(Fault.values()[random.nextInt(Fault.values().length)]);
        }
      }
      out.add(build(i, set));
    }
    return out;
  }

  private EvalCase build(int i, Set<Fault> faults) {
    var no =
        faults.contains(Fault.BLANK_VOUCHER_NO)
            ? (random.nextBoolean() ? "" : "  ")
            : "MH%06d".formatted(i);
    var tax =
        faults.contains(Fault.BAD_TAX_CODE)
            ? BAD_TAX_CODES[random.nextInt(BAD_TAX_CODES.length)]
            : validTaxCode();
    var date =
        faults.contains(Fault.FUTURE_DATE)
            ? LocalDate.now().plusDays(1 + random.nextInt(400))
            : LocalDate.of(2026, 1, 1).plusDays(random.nextInt(200));
    var lines = new ArrayList<ErpJournalLine>();
    int n = 1 + random.nextInt(4);
    BigDecimal total = BigDecimal.ZERO;
    for (int l = 1; l <= n; l++) {
      var amount = BigDecimal.valueOf(100_000L * (1 + random.nextInt(500)));
      var vat = amount.divide(BigDecimal.TEN, 0, java.math.RoundingMode.HALF_UP);
      total = total.add(amount).add(vat);
      lines.add(
          new ErpJournalLine(
              l,
              "Hàng " + l,
              "1561",
              "331",
              amount,
              amount,
              null,
              BigDecimal.ONE,
              amount,
              BigDecimal.TEN,
              vat,
              "1331"));
    }
    var voucher =
        new ErpVoucher(
            "eval-%06d".formatted(i),
            COMPANY,
            ErpEntityType.PURCHASE_INVOICE,
            210,
            no,
            date,
            date,
            "Mua hàng tổng hợp",
            new ErpPartnerRef("p" + i, "NCC" + i, "Đối tác " + i, tax),
            "VND",
            BigDecimal.ONE,
            total,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            true,
            lines,
            null);
    var expected = new java.util.HashSet<String>();
    faults.forEach(f -> expected.add(f.ruleId));
    return new EvalCase(voucher, Set.copyOf(expected));
  }

  private String validTaxCode() {
    var sb = new StringBuilder("0");
    for (int i = 0; i < 9; i++) sb.append(random.nextInt(10));
    if (random.nextInt(5) == 0) sb.append('-').append("%03d".formatted(random.nextInt(1000)));
    return sb.toString();
  }
}
