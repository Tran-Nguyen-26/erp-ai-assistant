package vn.edu.uet.erpai.common.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;

/**
 * Xuất bảng ra PDF (A4 ngang) có tiêu đề, nhiều trang, tự xuống dòng. Nhúng font DejaVu Sans để
 * hiển thị đúng tiếng Việt (font chuẩn của PDF không có dấu).
 */
public final class PdfTableWriter {

  public static final String CONTENT_TYPE = "application/pdf";

  private static final float MARGIN = 36f;
  private static final float TITLE_SIZE = 15f;
  private static final float FONT_SIZE = 8.5f;
  private static final float LEADING = 11f;
  private static final float CELL_PAD = 3f;

  private PdfTableWriter() {}

  /**
   * @param widthWeights tỷ lệ độ rộng các cột (cùng số phần tử với header)
   */
  public static byte[] write(
      String title,
      String subtitle,
      List<String> header,
      List<Float> widthWeights,
      List<List<String>> rows) {
    try (var doc = new PDDocument();
        var out = new ByteArrayOutputStream();
        var fontStream = new ClassPathResource("fonts/DejaVuSans.ttf").getInputStream()) {
      PDFont font = PDType0Font.load(doc, fontStream);
      var page = PDRectangle.A4;
      var landscape = new PDRectangle(page.getHeight(), page.getWidth());
      float usable = landscape.getWidth() - 2 * MARGIN;
      float total = widthWeights.stream().reduce(0f, Float::sum);
      var widths = widthWeights.stream().map(w -> usable * w / total).toList();

      var ctx = new Ctx(doc, font, landscape);
      ctx.newPage(title, subtitle);
      ctx.row(header, widths, true);
      for (var row : rows) {
        ctx.row(row, widths, false, header, title, subtitle);
      }
      ctx.close();
      doc.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static final class Ctx {
    final PDDocument doc;
    final PDFont font;
    final PDRectangle size;
    PDPageContentStream cs;
    float y;

    Ctx(PDDocument doc, PDFont font, PDRectangle size) {
      this.doc = doc;
      this.font = font;
      this.size = size;
    }

    void newPage(String title, String subtitle) throws IOException {
      close();
      var p = new PDPage(size);
      doc.addPage(p);
      cs = new PDPageContentStream(doc, p);
      y = size.getHeight() - MARGIN;
      text(title, MARGIN, y - TITLE_SIZE, TITLE_SIZE);
      y -= TITLE_SIZE + 6;
      if (subtitle != null && !subtitle.isBlank()) {
        text(subtitle, MARGIN, y - FONT_SIZE, FONT_SIZE);
        y -= FONT_SIZE + 8;
      }
      y -= 4;
    }

    void row(List<String> cells, List<Float> widths, boolean header) throws IOException {
      draw(cells, widths, header);
    }

    void row(
        List<String> cells,
        List<Float> widths,
        boolean header,
        List<String> head,
        String title,
        String subtitle)
        throws IOException {
      var wrapped = wrap(cells, widths);
      float height = wrapped.stream().mapToInt(List::size).max().orElse(1) * LEADING + 2 * CELL_PAD;
      if (y - height < MARGIN) {
        newPage(title, subtitle);
        draw(head, widths, true);
      }
      draw(cells, widths, header);
    }

    private void draw(List<String> cells, List<Float> widths, boolean header) throws IOException {
      var wrapped = wrap(cells, widths);
      int lines = wrapped.stream().mapToInt(List::size).max().orElse(1);
      float height = lines * LEADING + 2 * CELL_PAD;
      float x = MARGIN;
      for (int c = 0; c < widths.size(); c++) {
        if (header) {
          cs.setNonStrokingColor(0.9f);
          cs.addRect(x, y - height, widths.get(c), height);
          cs.fill();
          cs.setNonStrokingColor(0f);
        }
        var cellLines = c < wrapped.size() ? wrapped.get(c) : List.<String>of();
        for (int l = 0; l < cellLines.size(); l++) {
          text(
              cellLines.get(l),
              x + CELL_PAD,
              y - CELL_PAD - FONT_SIZE - l * LEADING + 2,
              FONT_SIZE);
        }
        x += widths.get(c);
      }
      cs.setStrokingColor(0.55f);
      x = MARGIN;
      for (int c = 0; c < widths.size(); c++) {
        cs.addRect(x, y - height, widths.get(c), height);
        x += widths.get(c);
      }
      cs.stroke();
      cs.setStrokingColor(0f);
      y -= height;
    }

    private List<List<String>> wrap(List<String> cells, List<Float> widths) throws IOException {
      var out = new ArrayList<List<String>>();
      for (int c = 0; c < widths.size(); c++) {
        out.add(
            wrapText(
                c < cells.size() && cells.get(c) != null ? cells.get(c) : "",
                widths.get(c) - 2 * CELL_PAD));
      }
      return out;
    }

    private List<String> wrapText(String text, float maxWidth) throws IOException {
      var lines = new ArrayList<String>();
      for (var paragraph : text.replace("\r", "").split("\n", -1)) {
        var current = new StringBuilder();
        for (var word : paragraph.split(" ")) {
          for (var piece : breakLong(word, maxWidth)) {
            var candidate = current.length() == 0 ? piece : current + " " + piece;
            if (width(candidate) <= maxWidth || current.length() == 0) {
              current = new StringBuilder(candidate);
            } else {
              lines.add(current.toString());
              current = new StringBuilder(piece);
            }
          }
        }
        lines.add(current.toString());
      }
      return lines;
    }

    /** Cắt từ quá dài (GUID, chuỗi liền) thành các đoạn vừa độ rộng ô. */
    private List<String> breakLong(String word, float maxWidth) throws IOException {
      if (width(word) <= maxWidth) {
        return List.of(word);
      }
      var parts = new ArrayList<String>();
      var cur = new StringBuilder();
      for (char ch : word.toCharArray()) {
        if (width(cur.toString() + ch) > maxWidth && cur.length() > 0) {
          parts.add(cur.toString());
          cur.setLength(0);
        }
        cur.append(ch);
      }
      parts.add(cur.toString());
      return parts;
    }

    private float width(String s) throws IOException {
      return font.getStringWidth(sanitize(s)) / 1000f * FONT_SIZE;
    }

    private void text(String s, float x, float baseline, float fontSize) throws IOException {
      cs.beginText();
      cs.setFont(font, fontSize);
      cs.newLineAtOffset(x, baseline);
      cs.showText(sanitize(s));
      cs.endText();
    }

    /** Thay ký tự font không có glyph (emoji...) bằng '?' để không làm hỏng việc xuất. */
    private String sanitize(String s) {
      var sb = new StringBuilder();
      for (int i = 0; i < s.length(); ) {
        int cp = s.codePointAt(i);
        i += Character.charCount(cp);
        if (cp == '\t') {
          sb.append(' ');
        } else if (cp < 0x20) {
          continue;
        } else {
          try {
            font.encode(new String(Character.toChars(cp)));
            sb.appendCodePoint(cp);
          } catch (IllegalArgumentException | IOException e) {
            sb.append('?');
          }
        }
      }
      return sb.toString();
    }

    void close() throws IOException {
      if (cs != null) {
        cs.close();
        cs = null;
      }
    }
  }
}
