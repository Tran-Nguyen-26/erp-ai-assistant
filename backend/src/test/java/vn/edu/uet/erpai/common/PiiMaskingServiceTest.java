package vn.edu.uet.erpai.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.common.pii.PiiMaskingService;

class PiiMaskingServiceTest {

  private final PiiMaskingService service = new PiiMaskingService();

  @Test
  void masksTaxCodesAccountsAndPartnerNamesAndRestoresThem() {
    var text =
        "NCC Công ty TNHH Alpha, MST 0101234567-001, MST cá nhân 001234567890, TK 12345678901234 lệch 1050000";
    var masked = service.mask(text, List.of("Công ty TNHH Alpha"));

    assertThat(masked.text())
        .doesNotContain("Alpha", "0101234567", "001234567890", "12345678901234")
        .contains("[[PARTNER_1]]", "[[TAX_1]]", "[[TAX_2]]", "[[ACCT_1]]")
        .contains("1050000"); // số tiền 7 chữ số không bị coi là PII
    assertThat(masked.unmask(masked.text())).isEqualTo(text);
  }

  @Test
  void sameValueGetsSameTokenAndNullStringsAreIgnored() {
    var masked =
        service.mask(
            "MST 0101234567 và lại 0101234567", java.util.Arrays.asList((String) null, "x"));
    assertThat(masked.text()).isEqualTo("MST [[TAX_1]] và lại [[TAX_1]]");
  }
}
