package vn.edu.uet.erpai.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import vn.edu.uet.erpai.auth.service.AuthService;
import vn.edu.uet.erpai.common.security.Role;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.service.ErpQueryService;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewJobIT {

  static final RabbitMQContainer RABBIT =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));

  static {
    RABBIT.start();
  }

  @DynamicPropertySource
  static void rabbit(DynamicPropertyRegistry r) {
    vn.edu.uet.erpai.TestInfra.registerPostgres(r);
    r.add("spring.rabbitmq.host", RABBIT::getHost);
    r.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    r.add("spring.rabbitmq.username", () -> "guest");
    r.add("spring.rabbitmq.password", () -> "guest");
    r.add("spring.rabbitmq.listener.simple.auto-startup", () -> "true");
    r.add("spring.rabbitmq.listener.simple.retry.initial-interval", () -> "200ms");
  }

  private static final String COMPANY = "eeeeeeee-0000-0000-0000-000000000005";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired AuthService authService;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean ErpQueryService erp;

  private String login(String username, Role role) throws Exception {
    if (!authService.existsByUsername(username)) {
      authService.createUser(username, "pw", username, role, COMPANY);
    }
    var body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + username + "\",\"password\":\"pw\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + mapper.readTree(body).get("accessToken").asText();
  }

  private static ErpVoucher voucher(String ref, String taxCode) {
    return new ErpVoucher(
        ref,
        COMPANY,
        ErpEntityType.PURCHASE_INVOICE,
        210,
        "MH-" + ref,
        LocalDate.of(2026, 10, 15),
        LocalDate.of(2026, 10, 15),
        "d",
        new ErpPartnerRef("p", "N", "Alpha", taxCode),
        "VND",
        BigDecimal.ONE,
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        true,
        List.of(),
        null);
  }

  private static List<String> refs(int n) {
    return IntStream.range(0, n).mapToObj(i -> String.format("ref-%04d", i)).toList();
  }

  private JsonNode createJob(String token, String from, String to, int expected) throws Exception {
    var body =
        mvc.perform(
                post("/api/v1/review-jobs")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"periodFrom\":\"" + from + "\",\"periodTo\":\"" + to + "\"}"))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body);
  }

  private JsonNode job(String token, String id) throws Exception {
    return mapper.readTree(
        mvc.perform(get("/api/v1/review-jobs/" + id).header("Authorization", token))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void awaitFinal(String token, String id, int seconds) {
    await()
        .atMost(seconds, TimeUnit.SECONDS)
        .until(
            () -> {
              var s = job(token, id).get("status").asText();
              return s.equals("COMPLETED") || s.equals("FAILED") || s.equals("CANCELLED");
            });
  }

  @Test
  void batchesAreProcessedInParallelSafeWayAndFailedBatchGoesToDlq() throws Exception {
    var chief = login("j_chief", Role.CHIEF_ACCOUNTANT);
    var acc = login("j_acc", Role.ACCOUNTANT);

    // phân quyền + validate
    createJob(acc, "2026-10-01", "2026-10-31", 403);
    createJob(chief, "2025-01-01", "2026-12-31", 422);

    // ----- Job 1: 450 chứng từ → 3 batch (200/200/50), mỗi chứng từ thứ 10 có MST sai -----
    var all = refs(450);
    doReturn(450L).when(erp).countVouchers(any(), anyString(), any(), any());
    doReturn(all).when(erp).listVoucherIds(any(), anyString(), any(), any(), anyLong(), anyInt());
    doAnswer(
            inv -> {
              String ref = inv.getArgument(2);
              int i = Integer.parseInt(ref.substring(4));
              return voucher(ref, i % 10 == 0 ? "12345" : "0101234567");
            })
        .when(erp)
        .getVoucher(any(), anyString(), anyString());

    var created = createJob(chief, "2026-10-01", "2026-10-31", 202);
    var id1 = created.get("id").asText();
    assertThat(created.get("totalBatches").asInt()).isEqualTo(3);
    assertThat(created.get("progress").get("totalDocuments").asInt()).isEqualTo(450);

    awaitFinal(chief, id1, 60);
    var done = job(chief, id1);
    assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(done.get("progress").get("processedDocuments").asInt()).isEqualTo(450);
    assertThat(done.get("progress").get("percent").asDouble()).isEqualTo(100.0);
    assertThat(done.get("violationCount").asInt()).isEqualTo(45);
    assertThat(done.get("summary").get("WARNING").asLong()).isEqualTo(45);
    mvc.perform(get("/api/v1/violations?reviewJobId=" + id1).header("Authorization", chief))
        .andExpect(jsonPath("$.totalItems").value(45));

    // ----- Job 2: batch thứ 2 luôn lỗi → retry → DLQ; job vẫn kết thúc, failedBatches = 1 -----
    var ids250 = refs(250);
    doReturn(250L).when(erp).countVouchers(any(), anyString(), any(), any());
    doReturn(ids250)
        .when(erp)
        .listVoucherIds(any(), anyString(), any(), any(), anyLong(), anyInt());
    doAnswer(
            inv -> {
              String ref = inv.getArgument(2);
              if (Integer.parseInt(ref.substring(4)) >= 200)
                throw new IllegalStateException("ERP down");
              return voucher(ref, "0101234567");
            })
        .when(erp)
        .getVoucher(any(), anyString(), anyString());
    var id2 = createJob(chief, "2026-09-01", "2026-09-30", 202).get("id").asText();
    // trùng kỳ khi job đang chạy → 409
    createJob(chief, "2026-09-01", "2026-09-30", 409);

    awaitFinal(chief, id2, 60);
    var partial = job(chief, id2);
    assertThat(partial.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(partial.get("progress").get("failedBatches").asInt()).isEqualTo(1);
    assertThat(partial.get("progress").get("processedDocuments").asInt()).isEqualTo(250);

    // job của công ty khác không truy cập được
    mvc.perform(
            get("/api/v1/review-jobs/" + java.util.UUID.randomUUID())
                .header("Authorization", chief))
        .andExpect(status().isNotFound());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'REVIEW_JOB_CREATED'",
                Integer.class))
        .isGreaterThanOrEqualTo(2);
  }

  @Test
  void runningJobCanBeCancelledAndStaysCancelled() throws Exception {
    var chief = login("j_chief2", Role.CHIEF_ACCOUNTANT);
    var acc = login("j_acc2", Role.ACCOUNTANT);
    var all = refs(400);
    doReturn(400L).when(erp).countVouchers(any(), anyString(), any(), any());
    doReturn(all).when(erp).listVoucherIds(any(), anyString(), any(), any(), anyLong(), anyInt());
    doAnswer(
            inv -> {
              Thread.sleep(25); // làm chậm để kịp hủy giữa chừng
              return voucher(inv.getArgument(2), "0101234567");
            })
        .when(erp)
        .getVoucher(any(), anyString(), anyString());

    var id = createJob(chief, "2026-08-01", "2026-08-31", 202).get("id").asText();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .until(() -> job(chief, id).get("status").asText().equals("RUNNING"));

    mvc.perform(post("/api/v1/review-jobs/" + id + "/cancel").header("Authorization", acc))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/review-jobs/" + id + "/cancel").header("Authorization", chief))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mvc.perform(post("/api/v1/review-jobs/" + id + "/cancel").header("Authorization", chief))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REVIEW_JOB_NOT_CANCELLABLE"));

    Thread.sleep(2500); // worker đang dở batch phải dừng và không ghi đè trạng thái
    var finalState = job(chief, id);
    assertThat(finalState.get("status").asText()).isEqualTo("CANCELLED");
    assertThat(finalState.get("progress").get("processedDocuments").asInt()).isLessThan(400);
  }

  @Test
  void jobCanCoverSeveralVoucherTypesAtOnce() throws Exception {
    var chief = login("j_chief3", Role.CHIEF_ACCOUNTANT);
    doAnswer(inv -> ((ErpEntityType) inv.getArgument(0)) == ErpEntityType.SALES_INVOICE ? 3L : 2L)
        .when(erp)
        .countVouchers(any(), anyString(), any(), any());
    doAnswer(
            inv ->
                ((ErpEntityType) inv.getArgument(0)) == ErpEntityType.SALES_INVOICE
                    ? List.of("s-1@2026-07-01", "s-2@2026-07-02", "s-3@2026-07-03")
                    : List.of("p-1", "p-2"))
        .when(erp)
        .listVoucherIds(any(), anyString(), any(), any(), anyLong(), anyInt());
    doAnswer(
            inv -> {
              ErpEntityType type = inv.getArgument(0);
              String ref = inv.getArgument(2);
              var base = voucher(ref, "12345");
              return new ErpVoucher(
                  ref,
                  COMPANY,
                  type,
                  1,
                  base.voucherNo(),
                  base.voucherDate(),
                  base.postedDate(),
                  "d",
                  base.partner(),
                  "VND",
                  BigDecimal.ONE,
                  BigDecimal.TEN,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  true,
                  List.of(),
                  null);
            })
        .when(erp)
        .getVoucher(any(), anyString(), anyString());

    var body =
        mapper.readTree(
            mvc.perform(
                    post("/api/v1/review-jobs")
                        .header("Authorization", chief)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"periodFrom\":\"2026-07-01\",\"periodTo\":\"2026-07-31\",\"entityTypes\":[\"SALES_INVOICE\",\"PAYMENT\"]}"))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString());
    var id = body.get("id").asText();
    assertThat(body.get("totalBatches").asInt()).isEqualTo(2); // mỗi loại một batch
    assertThat(body.get("progress").get("totalDocuments").asInt()).isEqualTo(5);

    awaitFinal(chief, id, 60);
    var done = job(chief, id);
    assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(done.get("progress").get("processedDocuments").asInt()).isEqualTo(5);
    assertThat(done.get("violationCount").asInt())
        .isEqualTo(5); // MST sai ở cả hóa đơn bán hàng lẫn phiếu chi
    mvc.perform(
            get("/api/v1/violations?reviewJobId=" + id + "&entityType=SALES_INVOICE")
                .header("Authorization", chief))
        .andExpect(jsonPath("$.totalItems").value(3));
    mvc.perform(
            get("/api/v1/violations?reviewJobId=" + id + "&entityType=PAYMENT")
                .header("Authorization", chief))
        .andExpect(jsonPath("$.totalItems").value(2));

    // loại chưa có adapter bị từ chối ngay khi tạo job
    mvc.perform(
            post("/api/v1/review-jobs")
                .header("Authorization", chief)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"periodFrom\":\"2026-06-01\",\"periodTo\":\"2026-06-30\",\"entityTypes\":[\"JOURNAL_ENTRY\"]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("RULE_ENTITY_TYPE_UNSUPPORTED"));
  }

  @Test
  void jobProgressCanBeStreamedUntilItCompletes() throws Exception {
    var chief = login("j_chief4", Role.CHIEF_ACCOUNTANT);
    var acc = login("j_acc4", Role.ACCOUNTANT);
    doReturn(3L).when(erp).countVouchers(any(), anyString(), any(), any());
    doReturn(List.of("e-1", "e-2", "e-3"))
        .when(erp)
        .listVoucherIds(any(), anyString(), any(), any(), anyLong(), anyInt());
    doAnswer(inv -> voucher(inv.getArgument(2), "0101234567"))
        .when(erp)
        .getVoucher(any(), anyString(), anyString());
    var id = createJob(chief, "2026-05-01", "2026-05-31", 202).get("id").asText();

    var sse =
        mvc.perform(
                get("/api/v1/review-jobs/" + id + "/events")
                    .header("Authorization", chief)
                    .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.request()
                    .asyncStarted())
            .andReturn();
    sse.getAsyncResult(30_000);
    var body = sse.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

    assertThat(body)
        .contains("event:completed")
        .contains("\"status\":\"COMPLETED\"")
        .contains("\"processedDocuments\":3");
    assertThat(body.indexOf("event:progress"))
        .isGreaterThanOrEqualTo(0)
        .isLessThan(body.indexOf("event:completed"));

    // job không tồn tại → 404 trước khi mở stream
    mvc.perform(
            get("/api/v1/review-jobs/" + java.util.UUID.randomUUID() + "/events")
                .header("Authorization", acc)
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isNotFound());
  }
}
