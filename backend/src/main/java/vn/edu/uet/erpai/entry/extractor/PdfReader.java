package vn.edu.uet.erpai.entry.extractor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.llm.LlmClient;

/**
 * Đọc PDF: lấy lớp văn bản; nếu là bản scan (gần như không có chữ) thì kết xuất trang thành ảnh
 * PNG.
 */
@Component
public class PdfReader {

  /**
   * @param pages ảnh các trang (rỗng khi có đủ lớp văn bản)
   */
  public record Content(String text, List<LlmClient.Image> pages) {

    public boolean isScanned() {
      return !pages.isEmpty();
    }
  }

  static final int MIN_TEXT_CHARS = 200;
  static final int MAX_PAGES = 3;
  private static final float DPI = 150f;

  public Content read(byte[] pdf) {
    try (var doc = Loader.loadPDF(pdf)) {
      var text = new PDFTextStripper().getText(doc).strip();
      if (text.length() >= MIN_TEXT_CHARS) {
        return new Content(text, List.of());
      }
      var renderer = new PDFRenderer(doc);
      var pages = new ArrayList<LlmClient.Image>();
      for (int i = 0; i < Math.min(doc.getNumberOfPages(), MAX_PAGES); i++) {
        BufferedImage img = renderer.renderImageWithDPI(i, DPI);
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        pages.add(new LlmClient.Image(out.toByteArray(), "image/png"));
      }
      if (pages.isEmpty()) {
        throw new BusinessException("ENTRY_PDF_EMPTY", "File PDF không có trang nào");
      }
      return new Content(text, pages);
    } catch (IOException e) {
      throw new BusinessException("ENTRY_PDF_INVALID", "Không đọc được file PDF");
    }
  }
}
