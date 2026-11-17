package vn.edu.uet.erpai.entry;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.entry.extractor.VnParsing;

class VnParsingTest {

  @Test
  void parsesVietnameseAndInternationalNumberFormats() {
    assertThat(VnParsing.number("1.250.000")).isEqualByComparingTo("1250000");
    assertThat(VnParsing.number("1,250,000")).isEqualByComparingTo("1250000");
    assertThat(VnParsing.number("1.250.000,50")).isEqualByComparingTo("1250000.50");
    assertThat(VnParsing.number("1,250,000.50")).isEqualByComparingTo("1250000.50");
    assertThat(VnParsing.number("10.500.000 đ")).isEqualByComparingTo("10500000");
    assertThat(VnParsing.number("2 000 000 VND")).isEqualByComparingTo("2000000");
    assertThat(VnParsing.number("12,5")).isEqualByComparingTo("12.5");
    assertThat(VnParsing.number("0,5")).isEqualByComparingTo("0.5");
    assertThat(VnParsing.number("-1.000")).isEqualByComparingTo("-1000");
    assertThat(VnParsing.number("500000")).isEqualByComparingTo("500000");
  }

  @Test
  void rejectsGarbageAsNull() {
    for (var bad : new String[] {null, "", "abc", "1.2.3,4,5x", "--1"}) {
      assertThat(VnParsing.number(bad)).as(String.valueOf(bad)).isNull();
    }
  }

  @Test
  void parsesDatesInCommonFormats() {
    var d = LocalDate.of(2026, 10, 5);
    assertThat(VnParsing.date("2026-10-05")).isEqualTo(d);
    assertThat(VnParsing.date("05/10/2026")).isEqualTo(d);
    assertThat(VnParsing.date("5-10-2026")).isEqualTo(d);
    assertThat(VnParsing.date("Ngày 05 tháng 10 năm 2026")).isEqualTo(d);
    assertThat(VnParsing.date("31/02/2026")).isNull();
    assertThat(VnParsing.date("không phải ngày")).isNull();
  }

  @Test
  void parsesVatRatesIncludingNonTaxableLabels() {
    assertThat(VnParsing.rate("10%").percent()).isEqualByComparingTo("10");
    assertThat(VnParsing.rate("8").percent()).isEqualByComparingTo("8");
    assertThat(VnParsing.rate("0,1").percent()).isEqualByComparingTo("10"); // phân số → phần trăm
    assertThat(VnParsing.rate("KCT").percent()).isNull();
    assertThat(VnParsing.rate("KCT").label()).isEqualTo("KCT");
    assertThat(VnParsing.rate("Không chịu thuế").label()).isEqualTo("KCT");
    assertThat(VnParsing.rate("KKKNT").label()).isEqualTo("KKKNT");
    assertThat(VnParsing.rate(null).percent()).isNull();
  }

  @Test
  void taxCodeChecksumIsASoftSignal() {
    // 010123456 + chữ số kiểm tra tính theo trọng số 31,29,23,21,19,17,13,7,3 → 8
    assertThat(VnParsing.taxCodeChecksumOk("0101234568")).isTrue();
    assertThat(VnParsing.taxCodeChecksumOk("0101234567")).isFalse();
    assertThat(VnParsing.taxCodeChecksumOk("123")).isFalse();
    assertThat(VnParsing.taxCodeChecksumOk(null)).isFalse();
    assertThat(new BigDecimal("1")).isNotNull();
  }
}
