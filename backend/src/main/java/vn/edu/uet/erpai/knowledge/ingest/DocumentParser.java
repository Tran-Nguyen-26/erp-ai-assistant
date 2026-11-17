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
      case "docx" -> docx(content);
      default ->
          throw new BusinessException(
              "KNOWLEDGE_UNSUPPORTED_FORMAT",
              "Định dạng chưa hỗ trợ: ." + ext + " (hỗ trợ pdf, docx, txt, md)");
    };
  }

  private static String pdf(byte[] content) {
    try (var doc = Loader.loadPDF(content)) {
      return new PDFTextStripper().getText(doc);
    } catch (IOException e) {
      throw new BusinessException("KNOWLEDGE_PARSE_FAILED", "Không đọc được file PDF");
    }
  }

  private static final long MAX_DOCX_XML_BYTES = 30L * 1024 * 1024;

  /**
   * Trích văn bản từ .docx: chỉ đọc word/document.xml trong gói ZIP (giới hạn dung lượng giải nén,
   * chặn DTD/thực thể ngoài). Mỗi đoạn (w:p) thành một dòng để LegalChunker nhận diện
   * Chương/Điều/Khoản.
   */
  private static String docx(byte[] content) {
    try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(content))) {
      java.util.zip.ZipEntry e;
      while ((e = zip.getNextEntry()) != null) {
        if (e.getName().equals("word/document.xml")) {
          var limited = new java.io.ByteArrayOutputStream();
          var buf = new byte[8192];
          long total = 0;
          int n;
          while ((n = zip.read(buf)) > 0) {
            total += n;
            if (total > MAX_DOCX_XML_BYTES) {
              throw new BusinessException(
                  "KNOWLEDGE_PARSE_FAILED", "File DOCX quá lớn khi giải nén");
            }
            limited.write(buf, 0, n);
          }
          return docxText(limited.toByteArray());
        }
      }
      throw new BusinessException(
          "KNOWLEDGE_PARSE_FAILED", "File không phải DOCX hợp lệ (thiếu word/document.xml)");
    } catch (IOException e) {
      throw new BusinessException("KNOWLEDGE_PARSE_FAILED", "Không đọc được file DOCX");
    }
  }

  private static String docxText(byte[] xml) {
    try {
      var f = javax.xml.stream.XMLInputFactory.newFactory();
      f.setProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD, false);
      f.setProperty(javax.xml.stream.XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      var r = f.createXMLStreamReader(new java.io.ByteArrayInputStream(xml));
      var sb = new StringBuilder();
      boolean inText = false;
      while (r.hasNext()) {
        switch (r.next()) {
          case javax.xml.stream.XMLStreamConstants.START_ELEMENT -> {
            switch (r.getLocalName()) {
              case "t" -> inText = true;
              case "tab" -> sb.append('\t');
              case "br" -> sb.append('\n');
              default -> {}
            }
          }
          case javax.xml.stream.XMLStreamConstants.END_ELEMENT -> {
            switch (r.getLocalName()) {
              case "t" -> inText = false;
              case "p" -> sb.append('\n');
              default -> {}
            }
          }
          case javax.xml.stream.XMLStreamConstants.CHARACTERS -> {
            if (inText) {
              sb.append(r.getText());
            }
          }
          default -> {}
        }
      }
      return sb.toString();
    } catch (javax.xml.stream.XMLStreamException e) {
      throw new BusinessException("KNOWLEDGE_PARSE_FAILED", "Nội dung DOCX không hợp lệ");
    }
  }
}
