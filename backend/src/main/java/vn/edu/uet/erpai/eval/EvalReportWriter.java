package vn.edu.uet.erpai.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/** Ghi báo cáo Markdown vào eval/reports/ (thư mục đã gitignore). */
public final class EvalReportWriter {

  private EvalReportWriter() {}

  public static String toMarkdown(RuleEvalReport r) {
    var sb = new StringBuilder();
    sb.append("# Báo cáo đánh giá rule\n\n");
    sb.append("- Thời điểm: ").append(Instant.now()).append('\n');
    sb.append("- Dữ liệu: chứng từ TỔNG HỢP cài lỗi có nhãn (không phải dữ liệu thật)\n");
    sb.append("- Số chứng từ: ").append(r.vouchers()).append('\n');
    sb.append(
        String.format(
            Locale.ROOT,
            "- Thời gian rà soát: %d ms (%.0f chứng từ/giây)%n%n",
            r.elapsedMillis(),
            r.vouchersPerSecond()));
    sb.append("| Rule | TP | FP | FN | Precision | Recall | F1 |\n|---|---|---|---|---|---|---|\n");
    r.perRule().forEach((id, s) -> row(sb, id, s));
    row(sb, "**Tổng**", r.overall());
    return sb.toString();
  }

  private static void row(StringBuilder sb, String name, RuleEvalReport.RuleStats s) {
    sb.append(
        String.format(
            Locale.ROOT,
            "| %s | %d | %d | %d | %.3f | %.3f | %.3f |%n",
            name,
            s.truePositives(),
            s.falsePositives(),
            s.falseNegatives(),
            s.precision(),
            s.recall(),
            s.f1()));
  }

  public static Path write(RuleEvalReport r, Path dir) throws IOException {
    Files.createDirectories(dir);
    var file = dir.resolve("rule-eval-" + Instant.now().toString().replace(':', '-') + ".md");
    Files.writeString(file, toMarkdown(r), StandardCharsets.UTF_8);
    return file;
  }
}
