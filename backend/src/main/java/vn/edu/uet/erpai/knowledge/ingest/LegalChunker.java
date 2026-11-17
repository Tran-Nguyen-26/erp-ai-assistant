package vn.edu.uet.erpai.knowledge.ingest;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tách văn bản pháp luật theo cấu trúc Chương → Mục → Điều → Khoản → Điểm. Mỗi chunk là một Điều
 * (nếu đủ ngắn) hoặc một Khoản; chunk vượt {@code maxTokens} được tách theo điểm/đoạn nhưng giữ
 * nguyên {@code headingPath}. Văn bản không có cấu trúc "Điều" (tài liệu hướng dẫn) được tách theo
 * đoạn, có overlap.
 */
public class LegalChunker {

  private static final Pattern CHAPTER =
      Pattern.compile("^(Chương\\s+[IVXLCDM\\d]+)\\b.*", Pattern.CASE_INSENSITIVE);
  private static final Pattern SECTION =
      Pattern.compile("^(Mục\\s+\\d+)\\b.*", Pattern.CASE_INSENSITIVE);
  private static final Pattern ARTICLE = Pattern.compile("^Điều\\s+(\\d+[a-z]?)\\s*[.:]?\\s*(.*)$");
  private static final Pattern CLAUSE = Pattern.compile("^(\\d+)\\.\\s+\\S.*");
  private static final Pattern POINT = Pattern.compile("^([a-zđ])\\)\\s+\\S.*");
  private static final Pattern PAGE_NOISE =
      Pattern.compile("^(-\\s*)?\\d{1,3}(\\s*-)?$|^Trang\\s+\\d+.*$", Pattern.CASE_INSENSITIVE);

  private final int maxTokens;
  private final int overlapTokens;

  public LegalChunker(int maxTokens, int overlapTokens) {
    this.maxTokens = maxTokens;
    this.overlapTokens = overlapTokens;
  }

  /** Ước lượng số token (xấp xỉ, tiếng Việt ~3 ký tự/token với BGE-M3). */
  public static int estimateTokens(String text) {
    return (int) Math.ceil(text.length() / 3.0);
  }

  public List<Chunk> chunk(String rawText) {
    var lines = clean(rawText);
    boolean hasArticles = lines.stream().anyMatch(l -> ARTICLE.matcher(l).matches());
    var out = new ArrayList<Chunk>();
    if (hasArticles) {
      legal(lines, out);
    } else {
      plain(lines, out);
    }
    return List.copyOf(out);
  }

  // ---------- văn bản pháp luật ----------

  private static final class Article {
    String chapter;
    String section;
    String no;
    String title;
    final List<String> intro = new ArrayList<>();
    final List<Clause> clauses = new ArrayList<>();

    String path() {
      var p = new ArrayList<String>();
      if (chapter != null) p.add(chapter);
      if (section != null) p.add(section);
      p.add(title.isBlank() ? "Điều " + no : "Điều " + no + ". " + title);
      return String.join(" > ", p);
    }
  }

  private static final class Clause {
    final String no;
    final List<String> lines = new ArrayList<>();

    Clause(String no) {
      this.no = no;
    }
  }

  private void legal(List<String> lines, List<Chunk> out) {
    String chapter = null;
    String section = null;
    Article current = null;
    var preamble = new ArrayList<String>();
    var articles = new ArrayList<Article>();
    Clause clause = null;
    for (var line : lines) {
      Matcher m;
      if ((m = CHAPTER.matcher(line)).matches()) {
        chapter = m.group(1);
        section = null;
        current = null;
        clause = null;
        continue;
      }
      if ((m = SECTION.matcher(line)).matches()) {
        section = m.group(1);
        current = null;
        clause = null;
        continue;
      }
      if ((m = ARTICLE.matcher(line)).matches()) {
        current = new Article();
        current.chapter = chapter;
        current.section = section;
        current.no = m.group(1);
        current.title = m.group(2).trim();
        articles.add(current);
        clause = null;
        continue;
      }
      if (current == null) {
        // tiêu đề chương (dòng tên chương), lời mở đầu...
        if (chapter == null && section == null) {
          preamble.add(line);
        }
        continue;
      }
      if ((m = CLAUSE.matcher(line)).matches()) {
        clause = new Clause(line.substring(0, line.indexOf('.')));
        current.clauses.add(clause);
        clause.lines.add(line);
      } else if (clause != null) {
        clause.lines.add(line);
      } else {
        current.intro.add(line);
      }
    }
    if (!preamble.isEmpty()) {
      emit(out, "Phần mở đầu", null, null, String.join("\n", preamble));
    }
    for (var a : articles) {
      emitArticle(a, out);
    }
  }

  private void emitArticle(Article a, List<Chunk> out) {
    var whole = new StringBuilder();
    if (!a.intro.isEmpty()) whole.append(String.join("\n", a.intro));
    for (var c : a.clauses) {
      if (whole.length() > 0) whole.append('\n');
      whole.append(String.join("\n", c.lines));
    }
    var text = whole.toString();
    if (text.isBlank()) {
      return; // điều chỉ có tiêu đề: không có nội dung để truy xuất
    }
    if (estimateTokens(text) <= maxTokens || a.clauses.isEmpty()) {
      emit(out, a.path(), a.no, null, text);
      return;
    }
    if (!a.intro.isEmpty()) {
      emit(out, a.path(), a.no, null, String.join("\n", a.intro));
    }
    for (var c : a.clauses) {
      emit(out, a.path() + " > Khoản " + c.no, a.no, c.no, String.join("\n", c.lines));
    }
  }

  /** Thêm chunk; nếu vượt giới hạn thì tách theo điểm rồi theo đoạn/câu, giữ headingPath. */
  private void emit(List<Chunk> out, String path, String article, String clause, String text) {
    if (estimateTokens(text) <= maxTokens) {
      add(out, path, article, clause, text);
      return;
    }
    var groups = new ArrayList<List<String>>();
    List<String> cur = null;
    for (var line : text.split("\n")) {
      if (cur == null || POINT.matcher(line).matches()) {
        cur = new ArrayList<>();
        groups.add(cur);
      }
      cur.add(line);
    }
    int part = 1;
    for (var g : groups) {
      var gt = String.join("\n", g);
      var pointMatch = POINT.matcher(g.get(0));
      var gpath = pointMatch.matches() ? path + " > Điểm " + pointMatch.group(1) : path;
      if (estimateTokens(gt) <= maxTokens) {
        add(out, gpath, article, clause, gt);
      } else {
        for (var piece : splitBySize(gt)) {
          add(out, gpath + " (phần " + part++ + ")", article, clause, piece);
        }
      }
    }
  }

  private List<String> splitBySize(String text) {
    var pieces = new ArrayList<String>();
    var sb = new StringBuilder();
    for (var sentence : text.split("(?<=[.;:!?])\\s+|\n")) {
      if (sb.length() > 0 && estimateTokens(sb + " " + sentence) > maxTokens) {
        pieces.add(sb.toString());
        sb.setLength(0);
      }
      if (estimateTokens(sentence) > maxTokens) { // câu quá dài: cắt cứng
        int step = maxTokens * 3;
        for (int i = 0; i < sentence.length(); i += step) {
          pieces.add(sentence.substring(i, Math.min(sentence.length(), i + step)));
        }
        continue;
      }
      if (sb.length() > 0) sb.append(' ');
      sb.append(sentence);
    }
    if (sb.length() > 0) pieces.add(sb.toString());
    return pieces;
  }

  private static void add(
      List<Chunk> out, String path, String article, String clause, String text) {
    var content = text.strip();
    if (!content.isEmpty()) {
      out.add(new Chunk(out.size(), path, article, clause, content, estimateTokens(content)));
    }
  }

  // ---------- tài liệu hướng dẫn (không có "Điều") ----------

  private void plain(List<String> lines, List<Chunk> out) {
    String heading = "Toàn văn";
    var buf = new StringBuilder();
    for (var line : lines) {
      if (line.startsWith("#")) {
        flushPlain(out, heading, buf);
        heading = line.replaceFirst("^#+\\s*", "");
        continue;
      }
      if (buf.length() > 0 && estimateTokens(buf + "\n" + line) > maxTokens) {
        var tail = tail(buf.toString());
        flushPlain(out, heading, buf);
        buf.append(tail);
      }
      if (buf.length() > 0) buf.append('\n');
      buf.append(line);
    }
    flushPlain(out, heading, buf);
  }

  private void flushPlain(List<Chunk> out, String heading, StringBuilder buf) {
    if (buf.toString().isBlank()) {
      buf.setLength(0);
      return;
    }
    emit(out, heading, null, null, buf.toString());
    buf.setLength(0);
  }

  private String tail(String text) {
    int chars = overlapTokens * 3;
    return text.length() <= chars ? "" : text.substring(text.length() - chars);
  }

  // ---------- làm sạch ----------

  private static List<String> clean(String raw) {
    var text = Normalizer.normalize(raw.replace("\r", ""), Normalizer.Form.NFC);
    var out = new ArrayList<String>();
    for (var l : text.split("\n")) {
      var line = l.strip().replaceAll("[ \\t]+", " ");
      if (!line.isEmpty() && !PAGE_NOISE.matcher(line).matches()) {
        out.add(line);
      }
    }
    return out;
  }
}
