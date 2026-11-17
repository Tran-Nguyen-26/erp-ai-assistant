package vn.edu.uet.erpai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.knowledge.retrieval.RerankerClient;
import vn.edu.uet.erpai.knowledge.service.RagProperties;

class RerankerClientTest {

  private final RagProperties props =
      new RagProperties(0.3, 512, 1024, 32, 20, 5, "http://reranker");

  @Test
  void mapsScoresBackToInputOrder() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://reranker/rerank"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"raw_scores\":false")))
        .andRespond(
            withSuccess(
                "[{\"index\":1,\"score\":0.93},{\"index\":0,\"score\":0.12}]",
                MediaType.APPLICATION_JSON));

    var scores = new RerankerClient(builder, props).rerank("q", List.of("a", "b"));

    assertThat(scores).containsExactly(0.12, 0.93);
    server.verify();
  }

  @Test
  void serverErrorBecomesBadGatewayBusinessException() {
    var builder = RestClient.builder();
    MockRestServiceServer.bindTo(builder)
        .build()
        .expect(requestTo("http://reranker/rerank"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> new RerankerClient(builder, props).rerank("q", List.of("a")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getCode()).isEqualTo("KNOWLEDGE_RERANKER_UNAVAILABLE"));
  }

  @Test
  void emptyInputSkipsNetwork() {
    assertThat(new RerankerClient(RestClient.builder(), props).rerank("q", List.of())).isEmpty();
  }
}
