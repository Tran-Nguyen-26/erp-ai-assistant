package vn.edu.uet.erpai.rule.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.regex.Pattern;

/** Các hàm thuần được phép dùng trong SpEL của rule: #abs #round #sum #isBlank #matches. */
public final class RuleFunctions {

  private RuleFunctions() {}

  public static BigDecimal abs(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v.abs();
  }

  /** Làm tròn HALF_UP về số nguyên (đồng). */
  public static BigDecimal round(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v.setScale(0, RoundingMode.HALF_UP);
  }

  public static BigDecimal sum(Collection<BigDecimal> values) {
    return values == null
        ? BigDecimal.ZERO
        : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** Số ngày từ hôm nay đến {@code date} (dương = tương lai, âm = quá khứ); null → 0. */
  public static long daysFromToday(java.time.LocalDate date) {
    return date == null
        ? 0
        : java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), date);
  }

  public static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  public static boolean matches(String value, String regex) {
    return value != null && Pattern.matches(regex, value);
  }
}
