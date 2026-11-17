package vn.edu.uet.erpai.knowledge.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.exception.BusinessException;

/** Trích văn bản từ PDF (có text layer) và file văn bản thuần (.txt, .md). */
@Component
public class DocumentParser {

  public String parse(byte[] content, String fileName) {
    var ext =
        fileName.contains(".")
            ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
            : "";
    return switch (ext) {
      case "txt", "md" -> new String(content, StandardCharsets.UTF_8);
      case "pdf" -> pdf(content);
      default ->
          throw new BusinessException(
              "KNOWLEDGE_UNSUPPORTED_FORMAT",
              "Định dạng chưa hỗ trợ: ." + ext + " (hỗ trợ pdf, txt, md)");
    };
  }

  private static String pdf(byte[] content) {
    try (var doc = Loader.loadPDF(content)) {
      return new PDFTextStripper().getText(doc);
    } catch (IOException e) {
      throw new BusinessException("KNOWLEDGE_PARSE_FAILED", "Không đọc được file PDF");
    }
  }
}
