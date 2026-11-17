package vn.edu.uet.erpai.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.uet.erpai.AbstractPostgresIT;
import vn.edu.uet.erpai.auth.service.AuthService;
import vn.edu.uet.erpai.common.security.Role;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.service.ErpQueryService;

@SpringBootTest
@AutoConfigureMockMvc
class ProposalFlowIT extends AbstractPostgresIT {

  private static final String COMPANY = "dddddddd-0000-0000-0000-000000000004";
  private static final String REF = "44444444-4444-4444-4444-444444444444";

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

  private ResultActions json(String token, String url, String body) throws Exception {
    return mvc.perform(
        post(url)
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private JsonNode read(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private String propose(String token, String violationId, String path, String to, int expected)
      throws Exception {
    var r =
        json(
                token,
                "/api/v1/violations/" + violationId + "/proposals",
                "{\"changes\":[{\"path\":\""
                    + path
                    + "\",\"to\":\""
                    + to
                    + "\"}],\"rationale\":\"Sửa MST\"}")
            .andExpect(status().is(expected));
    return r.andReturn().getResponse().getContentAsString();
  }

  @Test
  void fullProposalLifecycleWithVerificationApprovalAndExport() throws Exception {
    when(erp.getPurchaseInvoice(anyString(), anyString()))
        .thenReturn(
            new ErpVoucher(
                REF,
                COMPANY,
                ErpEntityType.PURCHASE_INVOICE,
                210,
                "MH88",
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 15),
                "Mua hàng",
                new ErpPartnerRef("p", "N", "Công ty A", "12345"),
                "VND",
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                List.of(),
                null));
    var acc = login("p_acc", Role.ACCOUNTANT);
    var chief1 = login("p_chief1", Role.CHIEF_ACCOUNTANT);
    var chief2 = login("p_chief2", Role.CHIEF_ACCOUNTANT);

    var check =
        read(
            json(
                acc,
                "/api/v1/reviews/check",
                "{\"entityType\":\"PURCHASE_INVOICE\",\"entityRef\":\"" + REF + "\"}"));
    var vid = check.get("violations").get(0).get("id").asText();

    // phân quyền + validate
    propose(acc, vid, "partner.taxCode", "0101234567", 403);
    assertThat(
            mapper
                .readTree(propose(chief1, vid, "lines[5].vatAmount", "1", 422))
                .get("code")
                .asText())
        .isEqualTo("PROPOSAL_PATH_INVALID");

    // kiểm chứng bằng rule: MST mới vẫn sai → không lưu
    var failed = mapper.readTree(propose(chief1, vid, "partner.taxCode", "123", 422));
    assertThat(failed.get("code").asText()).isEqualTo("PROPOSAL_VERIFICATION_FAILED");
    assertThat(failed.get("verification").get("passed").asBoolean()).isFalse();
    assertThat(failed.get("verification").get("remainingViolations")).isNotEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM proposal WHERE company_id = ?", Integer.class, COMPANY))
        .isZero();

    // đề xuất hợp lệ → PENDING, violation PROPOSED
    var first = mapper.readTree(propose(chief1, vid, "partner.taxCode", "0101234567", 201));
    assertThat(first.get("status").asText()).isEqualTo("PENDING");
    assertThat(first.get("changes").get(0).get("from").asText()).isEqualTo("12345");
    assertThat(first.get("verification").get("passed").asBoolean()).isTrue();
    mvc.perform(get("/api/v1/violations/" + vid).header("Authorization", acc))
        .andExpect(jsonPath("$.status").value("PROPOSED"));

    // đề xuất mới thay thế đề xuất cũ
    var second = mapper.readTree(propose(chief1, vid, "partner.taxCode", "0101234567-001", 201));
    mvc.perform(get("/api/v1/proposals/" + first.get("id").asText()).header("Authorization", acc))
        .andExpect(jsonPath("$.status").value("SUPERSEDED"));

    // người tạo không tự duyệt; từ chối cần lý do
    json(chief1, "/api/v1/proposals/" + second.get("id").asText() + "/approve", "{}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PROPOSAL_SELF_APPROVAL"));
    json(chief2, "/api/v1/proposals/" + second.get("id").asText() + "/reject", "{}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("REJECT_NOTE_REQUIRED"));
    json(
            chief2,
            "/api/v1/proposals/" + second.get("id").asText() + "/reject",
            "{\"note\":\"Cần xác minh với NCC\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
    mvc.perform(get("/api/v1/violations/" + vid).header("Authorization", acc))
        .andExpect(jsonPath("$.status").value("EXPLAINED"));

    // duyệt đề xuất mới → RESOLVED_PENDING_ERP; duyệt lại → 409
    var third = mapper.readTree(propose(chief1, vid, "partner.taxCode", "0101234567", 201));
    json(chief2, "/api/v1/proposals/" + third.get("id").asText() + "/approve", "{\"note\":\"OK\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
    mvc.perform(get("/api/v1/violations/" + vid).header("Authorization", acc))
        .andExpect(jsonPath("$.status").value("RESOLVED_PENDING_ERP"));
    json(chief2, "/api/v1/proposals/" + third.get("id").asText() + "/approve", "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_PENDING"));

    // xuất phiếu: chỉ đề xuất đã duyệt
    json(
            chief2,
            "/api/v1/proposals/export",
            "{\"proposalIds\":[\"" + second.get("id").asText() + "\"]}")
        .andExpect(status().isConflict());
    var export =
        json(
                chief2,
                "/api/v1/proposals/export",
                "{\"proposalIds\":[\"" + third.get("id").asText() + "\"],\"format\":\"CSV\"}")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertThat(export.getContentType()).startsWith("text/csv");
    var csv = export.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(csv).contains("partner.taxCode").contains("12345").contains("0101234567");
    json(
            chief2,
            "/api/v1/proposals/export",
            "{\"proposalIds\":[\"" + third.get("id").asText() + "\"],\"format\":\"DOCX\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("PROPOSAL_EXPORT_FORMAT_UNSUPPORTED"));
    mvc.perform(get("/api/v1/proposals/" + third.get("id").asText()).header("Authorization", acc))
        .andExpect(jsonPath("$.status").value("EXPORTED"));

    // không còn ghi ERP: mock chỉ dùng cho SELECT; audit log đầy đủ
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action IN ('PROPOSAL_CREATED','PROPOSAL_APPROVED','PROPOSAL_REJECTED','PROPOSAL_EXPORTED')",
                Integer.class))
        .isGreaterThanOrEqualTo(6);
    mvc.perform(get("/api/v1/proposals?status=EXPORTED").header("Authorization", acc))
        .andExpect(jsonPath("$.totalItems").value(1));
  }
}
