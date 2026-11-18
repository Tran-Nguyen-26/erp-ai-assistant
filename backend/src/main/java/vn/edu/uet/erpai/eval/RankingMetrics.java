package vn.edu.uet.erpai.eval;

import java.util.List;
import java.util.Set;

/**
 * Chỉ số xếp hạng cho đánh giá truy xuất RAG. Mỗi truy vấn: danh sách id đã xếp hạng + tập id đúng.
 */
public final class RankingMetrics {

  public record Query(List<String> ranked, Set<String> relevant) {}

  private RankingMetrics() {}

  /** Tỷ lệ truy vấn có ít nhất một kết quả đúng trong top-k. */
  public static double hitAtK(List<Query> queries, int k) {
    if (queries.isEmpty()) return 0;
    long hits =
        queries.stream()
            .filter(q -> q.ranked().stream().limit(k).anyMatch(q.relevant()::contains))
            .count();
    return (double) hits / queries.size();
  }

  /** Mean Reciprocal Rank: trung bình 1/hạng của kết quả đúng đầu tiên (0 nếu không có). */
  public static double mrr(List<Query> queries) {
    if (queries.isEmpty()) return 0;
    double sum = 0;
    for (var q : queries) {
      for (int i = 0; i < q.ranked().size(); i++) {
        if (q.relevant().contains(q.ranked().get(i))) {
          sum += 1.0 / (i + 1);
          break;
        }
      }
    }
    return sum / queries.size();
  }
}
