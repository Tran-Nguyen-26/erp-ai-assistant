package vn.edu.uet.erpai.common.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Ghi file .xlsx tối giản (OOXML) một sheet, không cần thư viện ngoài. Ô là chuỗi dạng inlineStr
 * nên mọi nội dung bắt đầu bằng "=", "+", "@" chỉ là văn bản, không bao giờ bị Excel coi là công
 * thức.
 */
public final class XlsxWriter {

  public static final String CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private XlsxWriter() {}

  /**
   * @param boldRows chỉ số hàng (bắt đầu từ 0) cần in đậm, ví dụ dòng tiêu đề
   */
  public static byte[] write(String sheetName, List<List<String>> rows, Set<Integer> boldRows) {
    try (var bytes = new ByteArrayOutputStream();
        var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      entry(
          zip,
          "[Content_Types].xml",
          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
              + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
              + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
              + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
              + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
              + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
              + "</Types>");
      entry(
          zip,
          "_rels/.rels",
          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
              + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
              + "</Relationships>");
      entry(
          zip,
          "xl/workbook.xml",
          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
              + "<sheets><sheet name=\""
              + escape(safeSheetName(sheetName))
              + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
      entry(
          zip,
          "xl/_rels/workbook.xml.rels",
          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
              + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
              + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
              + "</Relationships>");
      entry(
          zip,
          "xl/styles.xml",
          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
              + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
              + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
              + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
              + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
              + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
              + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
              + "</styleSheet>");
      entry(zip, "xl/worksheets/sheet1.xml", sheet(rows, boldRows));
      zip.finish();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String sheet(List<List<String>> rows, Set<Integer> bold) {
    int cols = rows.stream().mapToInt(List::size).max().orElse(1);
    var sb =
        new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            .append(
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            .append("<cols><col min=\"1\" max=\"")
            .append(cols)
            .append("\" width=\"24\" customWidth=\"1\"/></cols><sheetData>");
    for (int r = 0; r < rows.size(); r++) {
      sb.append("<row r=\"").append(r + 1).append("\">");
      var row = rows.get(r);
      for (int c = 0; c < row.size(); c++) {
        var value = row.get(c);
        if (value == null || value.isEmpty()) {
          continue;
        }
        sb.append("<c r=\"")
            .append(column(c))
            .append(r + 1)
            .append("\" t=\"inlineStr\"")
            .append(bold != null && bold.contains(r) ? " s=\"1\"" : "")
            .append("><is><t xml:space=\"preserve\">")
            .append(escape(value))
            .append("</t></is></c>");
      }
      sb.append("</row>");
    }
    return sb.append("</sheetData></worksheet>").toString();
  }

  /** 0 → A, 25 → Z, 26 → AA. */
  static String column(int index) {
    var sb = new StringBuilder();
    for (int i = index; i >= 0; i = i / 26 - 1) {
      sb.insert(0, (char) ('A' + i % 26));
    }
    return sb.toString();
  }

  private static String safeSheetName(String name) {
    var s = name == null || name.isBlank() ? "Sheet1" : name.replaceAll("[\\\\/?*\\[\\]:]", " ");
    return s.length() > 31 ? s.substring(0, 31) : s;
  }

  /** Escape XML và bỏ ký tự điều khiển không hợp lệ trong XML 1.0. */
  static String escape(String s) {
    var sb = new StringBuilder(s.length());
    for (char ch : s.toCharArray()) {
      switch (ch) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        case '"' -> sb.append("&quot;");
        default -> {
          if (ch == '\t' || ch == '\n' || ch == '\r' || ch >= 0x20) {
            sb.append(ch);
          }
        }
      }
    }
    return sb.toString();
  }

  private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
