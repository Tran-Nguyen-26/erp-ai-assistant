package vn.edu.uet.erpai.entry.extractor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/** Chuyển chuỗi do OCR/LLM sinh ra (định dạng Việt Nam) sang kiểu chuẩn; sai định dạng → null. */
public final class VnParsing {

  public record Rate(BigDecimal percent, String label) {}

  private static final Pattern DATE_ISO = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
  private static final Pattern DATE_DMY =
      Pattern.compile("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})");
  private static final Pattern DATE_TEXT =
      Pattern.compile("(?i)ngày\\s*(\\d{1,2})\\s*tháng\\s*(\\d{1,2})\\s*năm\\s*(\\d{4})");
  private static final Pattern RATE_PERCENT = Pattern.compile("^(\\d+(?:[.,]\\d+)?)\\s*%$");

  private VnParsing() {}

  /** "1.250.000", "1,250,000", "1.250.000,50", "1250000 đ" → BigDecimal. */
  public static BigDecimal number(String raw) {
    if (raw == null) {
      return null;
    }
    var s = raw.strip().replaceAll("(?i)(vnđ|vnd|đồng|₫|đ)", "").replaceAll("[\\s\\u00A0]", "");
    if (s.isEmpty() || !s.matches("[-+]?[\\d.,]+")) {
      return null;
    }
    boolean negative = s.startsWith("-");
    s = s.replaceFirst("^[-+]", "");
    int lastDot = s.lastIndexOf('.');
    int lastComma = s.lastIndexOf(',');
    String normalized;
    if (lastDot >= 0 && lastComma >= 0) {
      char decimal = lastDot > lastComma ? '.' : ',';
      var thousands = decimal == '.' ? "," : ".";
      normalized = s.replace(thousands, "").replace(decimal, '.');
    } else if (lastDot >= 0 || lastComma >= 0) {
      char sep = lastDot >= 0 ? '.' : ',';
      var parts = s.split(Pattern.quote(String.valueOf(sep)), -1);
      boolean thousands =
          parts.length > 1
              && !parts[0].equals("0")
              && parts[0].length() >= 1
              && parts[0].length() <= 3
              && Arrays.stream(parts).skip(1).allMatch(p -> p.length() == 3);
      if (thousands) {
        normalized = s.replace(String.valueOf(sep), "");
      } else if (parts.length == 2) {
        normalized = parts[0] + "." + parts[1];
      } else {
        return null;
      }
    } else {
      normalized = s;
    }
    try {
      var v = new BigDecimal(normalized);
      return negative ? v.negate() : v;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static LocalDate date(String raw) {
    if (raw == null) {
      return null;
    }
    var s = raw.strip();
    try {
      var m = DATE_ISO.matcher(s);
      if (m.find()) {
        return LocalDate.of(
            Integer.parseInt(m.group(1)),
            Integer.parseInt(m.group(2)),
            Integer.parseInt(m.group(3)));
      }
      m = DATE_TEXT.matcher(s);
      if (m.find()) {
        return LocalDate.of(
            Integer.parseInt(m.group(3)),
            Integer.parseInt(m.group(2)),
            Integer.parseInt(m.group(1)));
      }
      m = DATE_DMY.matcher(s);
      if (m.find()) {
        return LocalDate.of(
            Integer.parseInt(m.group(3)),
            Integer.parseInt(m.group(2)),
            Integer.parseInt(m.group(1)));
      }
    } catch (java.time.DateTimeException e) { // gồm cả DateTimeParseException
      return null;
    }
    return null;
  }

  /** "10%", "10", "0,1" → 10; "KCT"/"KKKNT"/"không chịu thuế" → thuế suất null kèm nhãn. */
  public static Rate rate(String raw) {
    if (raw == null || raw.isBlank()) {
      return new Rate(null, null);
    }
    var s = raw.strip();
    var m = RATE_PERCENT.matcher(s);
    if (m.matches()) {
      return new Rate(new BigDecimal(m.group(1).replace(',', '.')), s);
    }
    var n = number(s);
    if (n != null) {
      // "0,1" (phân số) → 10%; "10" → 10%
      var percent =
          n.signum() > 0 && n.compareTo(BigDecimal.ONE) < 0
              ? n.multiply(BigDecimal.valueOf(100))
              : n;
      return new Rate(percent.stripTrailingZeros().scale() < 0 ? percent.setScale(0) : percent, s);
    }
    var up = s.toUpperCase(Locale.ROOT);
    if (up.contains("KCT")
        || up.contains("KKKNT")
        || up.contains("KHÔNG CHỊU")
        || up.contains("KHÔNG KÊ KHAI")) {
      return new Rate(null, up.contains("KKKNT") || up.contains("KHÔNG KÊ KHAI") ? "KKKNT" : "KCT");
    }
    return new Rate(null, s);
  }

  /** Mã số thuế 10 số theo thuật toán chữ số kiểm tra; chỉ dùng làm tín hiệu (không bác bỏ). */
  public static boolean taxCodeChecksumOk(String taxCode) {
    if (taxCode == null) {
      return false;
    }
    var digits = taxCode.replaceAll("[^0-9]", "");
    if (digits.length() < 10) {
      return false;
    }
    int[] weights = {31, 29, 23, 21, 19, 17, 13, 7, 3};
    int sum = 0;
    for (int i = 0; i < 9; i++) {
      sum += (digits.charAt(i) - '0') * weights[i];
    }
    int check = 10 - (sum % 11);
    return check >= 0 && check <= 9 && check == digits.charAt(9) - '0';
  }
}
