package vn.edu.uet.erpai.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.common.export.PdfTableWriter;
import vn.edu.uet.erpai.common.export.XlsxWriter;

class ExportWritersTest {

  private static Map<String, String> unzip(byte[] bytes) throws Exception {
    var out = new HashMap<String, String>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
        out.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return out;
  }

  @Test
  void xlsxIsAWellFormedPackageWithEscapedVietnameseTextAndNoFormulas() throws Exception {
    var rows =
        List.of(
            List.of("Mã", "Lý do"),
            List.of("=HYPERLINK(\"http://x\")", "Tiền thuế < 10% & \"đúng\""),
            List.of("", "ô thứ hai"));
    var files = unzip(XlsxWriter.write("Đề nghị / điều chỉnh?", rows, Set.of(0)));

    assertThat(files.keySet())
        .contains(
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/worksheets/sheet1.xml",
            "xl/styles.xml");
    // mọi phần đều là XML hợp lệ
    var factory = DocumentBuilderFactory.newInstance();
    for (var xml : files.values()) {
      factory
          .newDocumentBuilder()
          .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
    var sheet = files.get("xl/worksheets/sheet1.xml");
    assertThat(sheet).contains("Tiền thuế &lt; 10% &amp; &quot;đúng&quot;");
    assertThat(sheet)
        .contains("t=\"inlineStr\"")
        .doesNotContain("<f>"); // chuỗi, không bao giờ là công thức
    assertThat(sheet)
        .contains("r=\"A1\" t=\"inlineStr\" s=\"1\"")
        .contains("r=\"B3\""); // tiêu đề in đậm; ô rỗng bị bỏ qua
    assertThat(files.get("xl/workbook.xml"))
        .contains("Đề nghị   điều chỉnh "); // ký tự cấm trong tên sheet được thay
  }

  @Test
  void pdfEmbedsVietnameseFontWrapsLongCellsAndPaginates() throws Exception {
    var rows = new java.util.ArrayList<List<String>>();
    for (int i = 1; i <= 120; i++) {
      rows.add(
          List.of(
              "DX-" + i,
              "Chứng từ 44444444-4444-4444-4444-444444444444-" + i,
              "lines[0].vatAmount",
              "Điều chỉnh tiền thuế theo hóa đơn gốc của người bán, số tiền chênh lệch do làm tròn "
                  + i));
    }
    var pdf =
        PdfTableWriter.write(
            "PHIẾU ĐỀ NGHỊ ĐIỀU CHỈNH",
            "Ngày lập: 17/11/2026 😀",
            List.of("Mã", "Chứng từ", "Trường", "Lý do"),
            List.of(1f, 2f, 1.5f, 3f),
            rows);

    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    try (var doc = Loader.loadPDF(pdf)) {
      assertThat(doc.getNumberOfPages()).isGreaterThan(1);
      var text = new PDFTextStripper().getText(doc);
      assertThat(text)
          .contains("PHIẾU ĐỀ NGHỊ ĐIỀU CHỈNH")
          .contains("Điều chỉnh tiền thuế")
          .contains("DX-120");
      // tiêu đề bảng lặp lại ở trang sau và emoji không làm hỏng việc xuất
      assertThat(text.split("lines\\[0\\]").length - 1).isGreaterThan(100);
      assertThat(text.split("Trường", -1).length - 1)
          .isGreaterThanOrEqualTo(doc.getNumberOfPages());
    }
  }
}
