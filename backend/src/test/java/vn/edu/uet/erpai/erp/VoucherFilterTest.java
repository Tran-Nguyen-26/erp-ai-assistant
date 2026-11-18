package vn.edu.uet.erpai.erp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import vn.edu.uet.erpai.erp.model.VoucherFilter;

class VoucherFilterTest {

  @Test
  void emptyFilterUsesEmptyStringsNotNulls() {
    var p = VoucherFilter.NONE.applyTo(new MapSqlParameterSource());
    assertThat(p.getValue("q")).isEqualTo("");
    assertThat(p.getValue("partnerTaxCode")).isEqualTo("");
    assertThat(new VoucherFilter(null, "  ").q()).isEmpty();
    assertThat(new VoucherFilter(null, "  ").partnerTaxCode()).isEmpty();
  }

  @Test
  void likeWildcardsInUserInputAreEscaped() {
    var p = new VoucherFilter(" 50%_[a]\\ ", "0101234567").applyTo(new MapSqlParameterSource());
    assertThat(p.getValue("q")).isEqualTo("50%_[a]\\");
    // dùng với ESCAPE '\' trong SQL: người dùng không chèn được ký tự đại diện
    assertThat(p.getValue("qLike")).isEqualTo("%50\\%\\_\\[a]\\\\%");
    assertThat(p.getValue("partnerTaxCode")).isEqualTo("0101234567");
  }
}
