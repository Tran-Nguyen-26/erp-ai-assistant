package vn.edu.uet.erpai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.knowledge.ingest.DocumentParser;
import vn.edu.uet.erpai.knowledge.ingest.LegalChunker;

class DocxParserTest {

  private final DocumentParser parser = new DocumentParser();

  static byte[] docx(String documentXml) throws Exception {
    var out = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
      zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("word/document.xml"));
      zip.write(documentXml.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return out.toByteArray();
  }

  static String paragraphs(String... texts) {
    var sb =
        new StringBuilder(
            "<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
    for (var t : texts) {
      sb.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(t).append("</w:t></w:r></w:p>");
    }
    return sb.append("</w:body></w:document>").toString();
  }

  @Test
  void extractsParagraphsAsLinesSoTheLegalChunkerCanFindArticles() throws Exception {
    var text =
        parser.parse(
            docx(
                paragraphs(
                    "Chương I",
                    "Điều 1. Phạm vi",
                    "1. Luật này quy định về thuế &amp; phí.",
                    "Điều 2. Đối tượng",
                    "Tổ chức, cá nhân.")),
            "luat.docx");

    assertThat(text).contains("Điều 1. Phạm vi\n").contains("thuế & phí.");
    var chunks = new LegalChunker(512, 0).chunk(text);
    assertThat(chunks)
        .extracting("headingPath")
        .contains("Chương I > Điều 1. Phạm vi", "Chương I > Điều 2. Đối tượng");
  }

  @Test
  void rejectsDoctypeExternalEntitiesNonDocxAndHugeContent() throws Exception {
    var xxe =
        "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><w:document xmlns:w=\"urn:w\"><w:p><w:t>&x;</w:t></w:p></w:document>";
    assertThatThrownBy(() -> parser.parse(docx(xxe), "a.docx"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getCode()).isEqualTo("KNOWLEDGE_PARSE_FAILED"));
    assertThatThrownBy(() -> parser.parse("không phải zip".getBytes(), "a.docx"))
        .isInstanceOf(BusinessException.class);

    var noDoc = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(noDoc)) {
      zip.putNextEntry(new ZipEntry("other.xml"));
      zip.write(1);
      zip.closeEntry();
    }
    assertThatThrownBy(() -> parser.parse(noDoc.toByteArray(), "a.docx"))
        .hasMessageContaining("thiếu word/document.xml");

    // zip bomb: document.xml giải nén vượt 30 MB
    var big = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(big)) {
      zip.putNextEntry(new ZipEntry("word/document.xml"));
      var chunk = new byte[1024 * 1024];
      java.util.Arrays.fill(chunk, (byte) 'a');
      for (int i = 0; i < 31; i++) zip.write(chunk);
      zip.closeEntry();
    }
    assertThatThrownBy(() -> parser.parse(big.toByteArray(), "a.docx"))
        .hasMessageContaining("quá lớn");
  }
}
