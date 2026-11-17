package vn.edu.uet.erpai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.knowledge.ingest.LegalChunker;

class LegalChunkerTest {

  private static final String LAW =
      """
      LUẬT THUẾ MẪU
      Chương I
      QUY ĐỊNH CHUNG
      Điều 1. Phạm vi điều chỉnh
      Luật này quy định về thuế.
      Điều 2. Đối tượng áp dụng
      1. Tổ chức sản xuất kinh doanh.
      2. Cá nhân kinh doanh.
      Chương II
      Mục 1
      Điều 3. Cách tính thuế
      1. Số thuế phải nộp bằng giá tính thuế nhân với thuế suất.
      a) Giá tính thuế là giá chưa có thuế.
      b) Thuế suất theo quy định.
      2. Việc làm tròn thực hiện theo quy định.
      - 3 -
      """;

  private final LegalChunker chunker = new LegalChunker(512, 64);

  @Test
  void shortArticleBecomesSingleChunkWithFullHeadingPath() {
    var chunks = chunker.chunk(LAW);
    var art3 = chunks.stream().filter(c -> "3".equals(c.articleNo())).toList();
    assertThat(art3).hasSize(1);
    assertThat(art3.get(0).headingPath()).isEqualTo("Chương II > Mục 1 > Điều 3. Cách tính thuế");
    assertThat(art3.get(0).content()).contains("giá tính thuế nhân với thuế suất");
  }

  @Test
  void trackChapterContextAndDropPageNumbers() {
    var chunks = chunker.chunk(LAW);
    assertThat(chunks.get(0).headingPath()).startsWith("Phần mở đầu");
    assertThat(chunks)
        .extracting("headingPath")
        .anySatisfy(p -> assertThat((String) p).isEqualTo("Chương I > Điều 1. Phạm vi điều chỉnh"));
    assertThat(chunks).noneMatch(c -> c.content().contains("- 3 -"));
    assertThat(chunks).extracting("index").containsExactly(0, 1, 2, 3);
  }

  @Test
  void oversizedArticleIsSplitByClauseKeepingHeadingPath() {
    var small = new LegalChunker(30, 0); // ~90 ký tự
    var chunks = small.chunk(LAW);
    var art3 = chunks.stream().filter(c -> "3".equals(c.articleNo())).toList();
    assertThat(art3.size()).isGreaterThan(1);
    assertThat(art3)
        .extracting("headingPath")
        .anySatisfy(p -> assertThat((String) p).contains("Điều 3. Cách tính thuế > Khoản 1"))
        .anySatisfy(p -> assertThat((String) p).contains("Khoản 2"));
    assertThat(art3)
        .allSatisfy(c -> assertThat(c.headingPath()).startsWith("Chương II > Mục 1 > Điều 3"));
  }

  @Test
  void oversizedClauseIsSplitByPoint() {
    var small = new LegalChunker(15, 0);
    var chunks = small.chunk(LAW);
    assertThat(chunks)
        .extracting("headingPath")
        .anySatisfy(p -> assertThat((String) p).contains("Khoản 1 > Điểm a"));
  }

  @Test
  void everyChunkRespectsTokenLimit() {
    var long1 = "1. " + "Nội dung rất dài. ".repeat(200);
    var chunks = new LegalChunker(100, 0).chunk("Điều 9. Dài\n" + long1);
    assertThat(chunks)
        .isNotEmpty()
        .allSatisfy(c -> assertThat(c.tokenCount()).isLessThanOrEqualTo(100));
  }

  @Test
  void documentWithoutArticlesIsChunkedByParagraphWithOverlap() {
    var paragraphs = new StringBuilder("# Hướng dẫn nhập hóa đơn\n");
    for (int i = 1; i <= 12; i++) {
      paragraphs.append("Bước ").append(i).append(": chọn chứng từ rồi kiểm tra các trường.\n");
    }
    var chunks = new LegalChunker(60, 10).chunk(paragraphs.toString());
    assertThat(chunks.size()).isGreaterThan(1);
    assertThat(chunks)
        .allSatisfy(c -> assertThat(c.headingPath()).isEqualTo("Hướng dẫn nhập hóa đơn"));
    // overlap: phần đầu chunk sau là phần cuối chunk trước
    var first = chunks.get(0).content();
    var tail = first.substring(first.length() - 20);
    assertThat(chunks.get(1).content()).contains(tail.substring(5, 15));
  }

  @Test
  void emptyInputGivesNoChunks() {
    assertThat(chunker.chunk("  \n \n")).isEmpty();
  }
}
