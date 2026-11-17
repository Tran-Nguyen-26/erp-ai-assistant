package vn.edu.uet.erpai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.uet.erpai.AbstractPostgresIT;
import vn.edu.uet.erpai.auth.service.AuthService;
import vn.edu.uet.erpai.common.security.Role;
import vn.edu.uet.erpai.knowledge.retrieval.Embedder;
import vn.edu.uet.erpai.knowledge.retrieval.RerankerClient;

@SpringBootTest
@AutoConfigureMockMvc
@Import(KnowledgeFlowIT.FakeAi.class)
class KnowledgeFlowIT extends AbstractPostgresIT {

  @TestConfiguration
  static class FakeAi {
    @Bean
    @Primary
    Embedder hashingEmbedder() {
      return new HashingEmbedder();
    }
  }

  private static final Path UPLOADS = tempDir();

  private static Path tempDir() {
    try {
      return Files.createTempDirectory("erpai-uploads");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry r) {
    r.add("app.storage.dir", UPLOADS::toString);
  }

  private static final String CURRENT_LAW =
      """
      Điều 3. Cách tính thuế
      1. Số thuế giá trị gia tăng bằng giá tính thuế nhân với thuế suất.
      Điều 5. Hóa đơn điện tử
      1. Hóa đơn điện tử phải có mã số thuế người bán.
      """;
  private static final String OLD_LAW =
      """
      Điều 7. Thuế suất cũ
      1. Thuế suất thông thường áp dụng giai đoạn trước năm 2025 là mười phần trăm.
      """;

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired AuthService authService;
  @MockitoBean RerankerClient reranker;

  private String login(String username, Role role) throws Exception {
    if (!authService.existsByUsername(username)) {
      authService.createUser(username, "pw-" + username, username, role, "C1");
    }
    var body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"" + username + "\",\"password\":\"pw-" + username + "\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + mapper.readTree(body).get("accessToken").asText();
  }

  private JsonNode upload(
      String token,
      String file,
      String text,
      String title,
      String from,
      String to,
      int expectedStatus)
      throws Exception {
    var req =
        multipart("/api/v1/knowledge/documents")
            .file(new MockMultipartFile("file", file, "text/plain", text.getBytes()))
            .param("title", title)
            .param("docNumber", title)
            .param("docType", "LAW")
            .header("Authorization", token);
    if (from != null) req.param("effectiveFrom", from);
    if (to != null) req.param("effectiveTo", to);
    var body =
        mvc.perform(req)
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body);
  }

  private String statusOf(String token, String id) throws Exception {
    var body =
        mvc.perform(get("/api/v1/knowledge/documents/" + id).header("Authorization", token))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body).get("status").asText();
  }

  private JsonNode search(String token, String query, String asOf) throws Exception {
    var body =
        mvc.perform(
                post("/api/v1/knowledge/search")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"query\":\"" + query + "\",\"asOf\":\"" + asOf + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body);
  }

  @Test
  void uploadIngestSearchRespectsEffectiveDatesAndThreshold() throws Exception {
    // Reranker giả, phụ thuộc câu hỏi: chỉ câu hỏi về "thuế suất" mới có chunk liên quan (0.9);
    // các câu hỏi khác không chunk nào liên quan (0.05) → phải "không đủ căn cứ".
    when(reranker.rerank(anyString(), anyList()))
        .thenAnswer(
            inv -> {
              String query = inv.getArgument(0);
              var texts = inv.<java.util.List<String>>getArgument(1);
              boolean asksRate = query.toLowerCase().contains("thuế suất");
              return texts.stream()
                  .mapToDouble(t -> asksRate && t.toLowerCase().contains("thuế suất") ? 0.9 : 0.05)
                  .toArray();
            });
    var admin = login("kadmin", Role.ADMIN);
    var accountant = login("kacc", Role.ACCOUNTANT);

    // chỉ ADMIN được tải lên
    upload(accountant, "x.txt", CURRENT_LAW, "NO", "2025-01-01", null, 403);

    var current = upload(admin, "current.txt", CURRENT_LAW, "LUAT-2025", "2025-01-01", null, 202);
    var old = upload(admin, "old.txt", OLD_LAW, "LUAT-2020", "2020-01-01", "2025-01-01", 202);
    var bad = upload(admin, "scan.docx", "x", "BAD", null, null, 202);

    for (var id :
        new String[] {current.get("id").asText(), old.get("id").asText(), bad.get("id").asText()}) {
      await()
          .atMost(15, TimeUnit.SECONDS)
          .until(
              () -> {
                var s = statusOf(admin, id);
                return !s.equals("PENDING") && !s.equals("INGESTING");
              });
    }
    assertThat(statusOf(admin, current.get("id").asText())).isEqualTo("READY");
    assertThat(statusOf(admin, old.get("id").asText())).isEqualTo("READY");
    assertThat(statusOf(admin, bad.get("id").asText())).isEqualTo("FAILED");

    // chunk giữ headingPath theo Điều
    var chunks =
        mapper.readTree(
            mvc.perform(
                    get("/api/v1/knowledge/documents/" + current.get("id").asText() + "/chunks")
                        .header("Authorization", admin))
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0).get("headingPath").asText()).isEqualTo("Điều 3. Cách tính thuế");

    // ngày chứng từ 2026 → chỉ văn bản hiện hành, có trích dẫn đúng Điều 3
    var now = search(accountant, "giá tính thuế nhân với thuế suất", "2026-10-15");
    assertThat(now.get("sufficient").asBoolean()).isTrue();
    var top = now.get("chunks").get(0);
    assertThat(top.get("headingPath").asText()).contains("Điều 3");
    assertThat(top.get("docNumber").asText()).isEqualTo("LUAT-2025");
    assertThat(now.get("chunks"))
        .allSatisfy(c -> assertThat(c.get("docNumber").asText()).isEqualTo("LUAT-2025"));

    // ngày chứng từ 2023 → văn bản cũ mới có hiệu lực
    var past = search(accountant, "thuế suất thông thường", "2023-06-01");
    assertThat(past.get("chunks")).isNotEmpty();
    assertThat(past.get("chunks"))
        .allSatisfy(c -> assertThat(c.get("docNumber").asText()).isEqualTo("LUAT-2020"));

    // câu hỏi ngoài phạm vi: rerank thấp → không đủ căn cứ, không trả chunk nào
    var none = search(accountant, "hóa đơn điện tử mã số thuế người bán", "2026-10-15");
    assertThat(none.get("sufficient").asBoolean()).isFalse();
    assertThat(none.get("chunks")).isEmpty();
  }
}
