package vn.edu.uet.erpai.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.review.domain.Proposal;
import vn.edu.uet.erpai.review.service.CreatorType;
import vn.edu.uet.erpai.review.service.ExportFormat;
import vn.edu.uet.erpai.review.service.ExportOnlyWriteBack;

class ExportFormatsTest {

  private final ExportOnlyWriteBack export = new ExportOnlyWriteBack();

  private static Proposal approved() {
    var p =
        new Proposal(
            UUID.randomUUID(),
            "c",
            "PURCHASE_INVOICE",
            "ref-1",
            Map.of(),
            Map.of(),
            List.of(Map.of("path", "partner.taxCode", "from", "12345", "to", "0101234568")),
            "Sửa MST theo hóa đơn gốc",
            List.of(),
            Map.of("passed", true),
            CreatorType.AGENT,
            UUID.randomUUID());
    p.approve(UUID.randomUUID(), "=cmd|' /C calc'!A0");
    return p;
  }

  @Test
  void producesCsvXlsxAndPdfFromTheSameRows() {
    var csv = export.export(List.of(approved()), ExportFormat.CSV);
    assertThat(csv.fileName()).endsWith(".csv");
    var text = new String(csv.content(), StandardCharsets.UTF_8);
    assertThat(text)
        .contains("partner.taxCode")
        .contains("0101234568")
        .contains("\"'=cmd"); // chặn công thức

    var xlsx = export.export(List.of(approved()), ExportFormat.XLSX);
    assertThat(xlsx.contentType()).contains("spreadsheetml");
    assertThat(xlsx.content()).startsWith(new byte[] {'P', 'K'});

    var pdf = export.export(List.of(approved()), ExportFormat.PDF);
    assertThat(pdf.contentType()).isEqualTo("application/pdf");
    assertThat(new String(pdf.content(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
  }

  @Test
  void formatParsingDefaultsToCsvAndRejectsUnknown() {
    assertThat(ExportFormat.parse(null)).isEqualTo(ExportFormat.CSV);
    assertThat(ExportFormat.parse(" pdf ")).isEqualTo(ExportFormat.PDF);
    assertThatThrownBy(() -> ExportFormat.parse("docx"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getCode()).isEqualTo("PROPOSAL_EXPORT_FORMAT_UNSUPPORTED"));
  }
}
