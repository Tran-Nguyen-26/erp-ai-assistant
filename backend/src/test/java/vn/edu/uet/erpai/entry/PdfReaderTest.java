package vn.edu.uet.erpai.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.entry.extractor.PdfReader;

class PdfReaderTest {

  private final PdfReader reader = new PdfReader();

  static byte[] pdf(String... pageTexts) throws Exception {
    try (var doc = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      for (var text : pageTexts) {
        var page = new PDPage();
        doc.addPage(page);
        try (var cs = new PDPageContentStream(doc, page)) {
          cs.beginText();
          cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
          cs.newLineAtOffset(40, 700);
          cs.showText(text);
          cs.endText();
        }
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void pdfWithTextLayerUsesTextAndRendersNothing() throws Exception {
    var text = "HOA DON GIA TRI GIA TANG So 0000123 Ky hieu C26TAA ".repeat(6);
    var c = reader.read(pdf(text));

    assertThat(c.isScanned()).isFalse();
    assertThat(c.text()).contains("0000123");
    assertThat(c.pages()).isEmpty();
  }

  @Test
  void scannedPdfIsRenderedToPngPagesCappedAtThree() throws Exception {
    var c = reader.read(pdf("", "", "", "", ""));

    assertThat(c.isScanned()).isTrue();
    assertThat(c.pages()).hasSize(3);
    var png = c.pages().get(0);
    assertThat(png.mimeType()).isEqualTo("image/png");
    assertThat(png.data()).startsWith(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
  }

  @Test
  void corruptPdfIsRejected() {
    assertThatThrownBy(() -> reader.read("không phải pdf".getBytes()))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("ENTRY_PDF_INVALID"));
  }
}
