package vn.edu.uet.erpai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.knowledge.retrieval.HybridRetriever;
import vn.edu.uet.erpai.knowledge.retrieval.RerankerClient;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievedChunk;
import vn.edu.uet.erpai.knowledge.service.KnowledgeSearchService;
import vn.edu.uet.erpai.knowledge.service.RagProperties;

class KnowledgeSearchServiceTest {

  private final HybridRetriever retriever = mock(HybridRetriever.class);
  private final RerankerClient reranker = mock(RerankerClient.class);
  private final RagProperties props = new RagProperties(0.3, 512, 1024, 32, 20, 2, "x");
  private final KnowledgeSearchService service =
      new KnowledgeSearchService(retriever, reranker, props);

  private static RetrievedChunk chunk(String content) {
    return new RetrievedChunk(
        UUID.randomUUID(), UUID.randomUUID(), "TT01", "t", "Điều 1", content, null, 0.03, null);
  }

  @Test
  void keepsOnlyChunksAboveMinScoreSortedByRerankAndLimitedToTopK() {
    when(retriever.retrieve(any(), any()))
        .thenReturn(List.of(chunk("a"), chunk("b"), chunk("c"), chunk("d")));
    when(reranker.rerank(any(), any())).thenReturn(new double[] {0.5, 0.9, 0.2, 0.7});

    var r = service.search("q", LocalDate.of(2026, 1, 1), null);

    assertThat(r.sufficient()).isTrue();
    assertThat(r.maxScore()).isEqualTo(0.9);
    assertThat(r.chunks()).extracting(RetrievedChunk::content).containsExactly("b", "d");
  }

  @Test
  void belowThresholdMeansInsufficientEvidence() {
    when(retriever.retrieve(any(), any())).thenReturn(List.of(chunk("a"), chunk("b")));
    when(reranker.rerank(any(), any())).thenReturn(new double[] {0.1, 0.29});

    var r = service.search("q", null, null);

    assertThat(r.sufficient()).isFalse();
    assertThat(r.chunks()).isEmpty();
    assertThat(r.maxScore()).isEqualTo(0.29);
  }

  @Test
  void noCandidatesMeansInsufficientWithoutCallingReranker() {
    when(retriever.retrieve(any(), any())).thenReturn(List.of());
    assertThat(service.search("q", null, null).sufficient()).isFalse();
    org.mockito.Mockito.verifyNoInteractions(reranker);
  }
}
