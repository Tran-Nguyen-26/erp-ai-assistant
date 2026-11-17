package vn.edu.uet.erpai.knowledge.retrieval;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.knowledge.service.RagProperties;

/** Gọi HuggingFace TEI {@code POST /rerank} (bge-reranker-v2-m3). */
@Component
public class RerankerClient {

  public record Score(int index, double score) {}

  private final RestClient client;

  /** Timeout cấu hình bằng spring.http.client.connect-timeout / read-timeout (áp lên builder). */
  public RerankerClient(RestClient.Builder builder, RagProperties props) {
    this.client = builder.baseUrl(props.rerankerBaseUrl()).build();
  }

  /**
   * @return điểm theo thứ tự đầu vào (score[i] ứng với texts[i])
   */
  public double[] rerank(String query, List<String> texts) {
    if (texts.isEmpty()) {
      return new double[0];
    }
    try {
      Score[] response =
          client
              .post()
              .uri("/rerank")
              .body(Map.of("query", query, "texts", texts, "raw_scores", false))
              .retrieve()
              .body(Score[].class);
      var scores = new double[texts.size()];
      if (response != null) {
        for (var s : response) {
          if (s.index() >= 0 && s.index() < scores.length) {
            scores[s.index()] = s.score();
          }
        }
      }
      return scores;
    } catch (RestClientException e) {
      throw new BusinessException(
          "KNOWLEDGE_RERANKER_UNAVAILABLE",
          "Dịch vụ rerank không khả dụng",
          HttpStatus.BAD_GATEWAY);
    }
  }
}
