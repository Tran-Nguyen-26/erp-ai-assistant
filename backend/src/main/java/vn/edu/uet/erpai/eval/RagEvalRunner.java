package vn.edu.uet.erpai.eval;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import vn.edu.uet.erpai.eval.RankingMetrics.Query;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievalResult;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievedChunk;

/**
 * Chạy bộ câu hỏi qua dịch vụ truy xuất thật (embedding + rerank) và tính Hit@k, MRR, tỷ lệ từ chối
 * đúng.
 */
public final class RagEvalRunner {

  /** Hàm truy xuất, tách ra để test không cần model/DB. */
  @FunctionalInterface
  public interface Searcher {
    RetrievalResult search(String query, LocalDate asOf);
  }

  private final Searcher searcher;

  public RagEvalRunner(Searcher searcher) {
    this.searcher = searcher;
  }

  public RagEvalReport run(List<RagQuestion> questions) {
    var ranked = new ArrayList<Query>();
    var latencies = new ArrayList<Long>();
    int unanswerable = 0;
    int refused = 0;
    for (var q : questions) {
      long start = System.nanoTime();
      var result = searcher.search(q.question(), q.asOf());
      latencies.add((System.nanoTime() - start) / 1_000_000);
      if (!q.answerable()) {
        unanswerable++;
        if (!result.sufficient()) refused++;
        continue;
      }
      var ids = new ArrayList<String>();
      var relevant = new java.util.HashSet<String>();
      for (var c : result.chunks()) {
        var id = c.chunkId() + "";
        ids.add(id);
        if (matches(c, q)) relevant.add(id);
      }
      ranked.add(new Query(ids, Set.copyOf(relevant)));
    }
    latencies.sort(null);
    return new RagEvalReport(
        questions.size(),
        ranked.size(),
        RankingMetrics.hitAtK(ranked, 1),
        RankingMetrics.hitAtK(ranked, 3),
        RankingMetrics.hitAtK(ranked, 5),
        RankingMetrics.mrr(ranked),
        unanswerable,
        unanswerable == 0 ? 1.0 : (double) refused / unanswerable,
        percentile(latencies, 0.50),
        percentile(latencies, 0.95));
  }

  static boolean matches(RetrievedChunk c, RagQuestion q) {
    return q.relevant().stream()
        .anyMatch(
            e ->
                e.docNumber() != null
                    && e.docNumber().equals(c.docNumber())
                    && c.headingPath() != null
                    && c.headingPath().startsWith(e.headingPath() == null ? "" : e.headingPath()));
  }

  private static long percentile(List<Long> sorted, double p) {
    if (sorted.isEmpty()) return 0;
    int idx = (int) Math.min(sorted.size() - 1, Math.ceil(p * sorted.size()) - 1);
    return sorted.get(Math.max(idx, 0));
  }
}
