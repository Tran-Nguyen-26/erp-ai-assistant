package vn.edu.uet.erpai.knowledge.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import vn.edu.uet.erpai.knowledge.retrieval.HybridRetriever;
import vn.edu.uet.erpai.knowledge.retrieval.RerankerClient;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievalResult;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievedChunk;

/**
 * API công khai để truy xuất tri thức: hybrid search → rerank → lọc theo ngưỡng. Nguyên tắc #4: nếu
 * điểm rerank cao nhất dưới {@code app.rag.min-score} thì {@code sufficient = false}.
 */
@Service
public class KnowledgeSearchService {

  private final HybridRetriever retriever;
  private final RerankerClient reranker;
  private final RagProperties props;

  public KnowledgeSearchService(
      HybridRetriever retriever, RerankerClient reranker, RagProperties props) {
    this.retriever = retriever;
    this.reranker = reranker;
    this.props = props;
  }

  public RetrievalResult search(String query, LocalDate asOf, Integer topK) {
    var candidates = retriever.retrieve(query, asOf == null ? LocalDate.now() : asOf);
    if (candidates.isEmpty()) {
      return new RetrievalResult(List.of(), 0.0, false);
    }
    var scores = reranker.rerank(query, candidates.stream().map(RetrievedChunk::content).toList());
    int limit = Math.min(topK == null ? props.topK() : topK, props.topK());
    var ranked =
        IntStream.range(0, candidates.size())
            .mapToObj(i -> candidates.get(i).withRerankScore(scores[i]))
            .sorted(Comparator.comparing(RetrievedChunk::rerankScore).reversed())
            .toList();
    double max = ranked.get(0).rerankScore();
    var kept =
        ranked.stream().filter(c -> c.rerankScore() >= props.minScore()).limit(limit).toList();
    return new RetrievalResult(kept, max, !kept.isEmpty());
  }
}
