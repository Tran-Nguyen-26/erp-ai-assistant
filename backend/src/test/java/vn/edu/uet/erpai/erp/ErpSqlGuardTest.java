package vn.edu.uet.erpai.erp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Bảo vệ nguyên tắc bất biến #2: SQL ERP chỉ đọc, có companyId, không SELECT *. */
class ErpSqlGuardTest {

  private static final Pattern WRITE =
      Pattern.compile(
          "\\b(INSERT|UPDATE|DELETE|MERGE|DROP|ALTER|CREATE|TRUNCATE|EXEC|EXECUTE|GRANT)\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SELECT_STAR =
      Pattern.compile("SELECT\\s+(\\w+\\.)?\\*|,\\s*\\w+\\.\\*", Pattern.CASE_INSENSITIVE);

  @Test
  void allErpSqlFilesAreReadOnlyScopedByCompany() throws Exception {
    var resources =
        new PathMatchingResourcePatternResolver().getResources("classpath:sql/erp/*.sql");
    assertThat(resources).isNotEmpty();
    for (var r : resources) {
      var sql = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      var code = sql.replaceAll("--[^\n]*", ""); // bỏ comment
      var name = r.getFilename();
      assertThat(WRITE.matcher(code).find()).as("%s chứa lệnh ghi", name).isFalse();
      assertThat(SELECT_STAR.matcher(code).find()).as("%s dùng SELECT *", name).isFalse();
      assertThat(code).as("%s thiếu :companyId", name).contains(":companyId");
    }
  }
}
