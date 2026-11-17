package vn.edu.uet.erpai.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.review.service.ExportOnlyWriteBack;

class ExportCsvTest {

  private static String csv(Object v) throws Exception {
    Method m = ExportOnlyWriteBack.class.getDeclaredMethod("csv", Object.class);
    m.setAccessible(true);
    return (String) m.invoke(null, v);
  }

  @Test
  void escapesQuotesAndNeutralizesFormulaInjection() throws Exception {
    assertThat(csv("a\"b")).isEqualTo("\"a\"\"b\"");
    assertThat(csv("=HYPERLINK(\"x\")")).startsWith("\"'=");
    assertThat(csv("@cmd")).startsWith("\"'@");
    assertThat(csv("-cmd|x")).startsWith("\"'-");
    assertThat(csv("-1050000.50")).isEqualTo("\"-1050000.50\""); // số âm hợp lệ giữ nguyên
    assertThat(csv(null)).isEmpty();
  }
}
