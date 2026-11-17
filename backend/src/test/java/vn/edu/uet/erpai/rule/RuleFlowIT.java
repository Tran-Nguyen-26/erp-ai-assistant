package vn.edu.uet.erpai.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.uet.erpai.AbstractPostgresIT;
import vn.edu.uet.erpai.auth.service.AuthService;
import vn.edu.uet.erpai.common.security.Role;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.rule.service.RuleSyncService;

@SpringBootTest
@AutoConfigureMockMvc
class RuleFlowIT extends AbstractPostgresIT {

  private static final String COMPANY_A = "aaaaaaaa-0000-0000-0000-000000000001";
  private static final String COMPANY_B = "bbbbbbbb-0000-0000-0000-000000000002";
  private static final String REF = "22222222-2222-2222-2222-222222222222";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired AuthService authService;
  @Autowired RuleSyncService syncService;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean ErpQueryService erp;

  private static ErpVoucher voucher(String taxCode) {
    return new ErpVoucher(
        REF,
        COMPANY_A,
        ErpEntityType.PURCHASE_INVOICE,
        210,
        "MH00123",
        LocalDate.of(2026, 10, 15),
        LocalDate.of(2026, 10, 15),
        "Mua hàng",
        new ErpPartnerRef("p", "NCC1", "Công ty A", taxCode),
        "VND",
        BigDecimal.ONE,
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        true,
        List.of(),
        null);
  }

  private String login(String username, Role role, String companyId) throws Exception {
    if (!authService.existsByUsername(username)) {
      authService.createUser(username, "pw-" + username, username, role, companyId);
    }
    var body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"" + username + "\",\"password\":\"pw-" + username + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + mapper.readTree(body).get("accessToken").asText();
  }

  private String check(String token) throws Exception {
    return mvc.perform(
            post("/api/v1/reviews/check")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityType\":\"PURCHASE_INVOICE\",\"entityRef\":\"" + REF + "\"}"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void checkUpsertsResolvesDismissesAndIsolatesCompanies() throws Exception {
    var accountant = login("acc_a", Role.ACCOUNTANT, COMPANY_A);

    // 1) chứng từ có MST sai → 1 violation OPEN
    when(erp.getPurchaseInvoice(anyString(), anyString())).thenReturn(voucher("12345"));
    var first = mapper.readTree(check(accountant));
    assertThat(first.get("violations")).hasSize(1);
    var v = first.get("violations").get(0);
    assertThat(v.get("ruleId").asText()).isEqualTo("FLD_TAX_CODE_FORMAT");
    assertThat(v.get("status").asText()).isEqualTo("OPEN");
    var violationId = v.get("id").asText();

    // 2) rà soát lại: không tạo trùng (fingerprint)
    var second = mapper.readTree(check(accountant));
    assertThat(second.get("violations").get(0).get("id").asText()).isEqualTo(violationId);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM violation WHERE company_id = ?", Integer.class, COMPANY_A))
        .isEqualTo(1);

    // 3) cô lập công ty: user công ty B không thấy
    var other = login("acc_b", Role.ACCOUNTANT, COMPANY_B);
    mvc.perform(get("/api/v1/violations/" + violationId).header("Authorization", other))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("VIOLATION_NOT_FOUND"));
    mvc.perform(get("/api/v1/violations").header("Authorization", other))
        .andExpect(jsonPath("$.totalItems").value(0));

    // 4) dismiss cần lý do
    mvc.perform(
            post("/api/v1/violations/" + violationId + "/dismiss")
                .header("Authorization", accountant)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            post("/api/v1/violations/" + violationId + "/dismiss")
                .header("Authorization", accountant)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"MST nước ngoài\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISMISSED"));
    mvc.perform(
            post("/api/v1/violations/" + violationId + "/reopen")
                .header("Authorization", accountant))
        .andExpect(jsonPath("$.status").value("OPEN"));

    // 5) sửa dữ liệu ERP → lần rà soát sau tự chuyển RESOLVED
    when(erp.getPurchaseInvoice(anyString(), anyString())).thenReturn(voucher("0101234567"));
    var fixed = mapper.readTree(check(accountant));
    assertThat(fixed.get("violations")).isEmpty();
    mvc.perform(get("/api/v1/violations?status=RESOLVED").header("Authorization", accountant))
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].status").value("RESOLVED"));
    mvc.perform(get("/api/v1/violations/summary").header("Authorization", accountant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.byStatus.RESOLVED").value(1));

    // 6) phát hiện lại → RESOLVED trở về OPEN
    when(erp.getPurchaseInvoice(anyString(), anyString())).thenReturn(voucher("12345"));
    var again = mapper.readTree(check(accountant));
    assertThat(again.get("violations").get(0).get("status").asText()).isEqualTo("OPEN");

    // 7) audit log đã ghi
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action IN ('RULE_CHECK_EXECUTED','VIOLATION_DISMISSED','VIOLATION_REOPENED')",
                Integer.class))
        .isGreaterThanOrEqualTo(5);
  }

  @Test
  void adminCanConfigureRulesOthersCannotAndSyncIsIdempotent() throws Exception {
    var admin = login("admin_a", Role.ADMIN, COMPANY_A);
    var accountant = login("acc_cfg", Role.ACCOUNTANT, COMPANY_A);

    mvc.perform(get("/api/v1/rules").header("Authorization", accountant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.ruleId=='FLD_TAX_CODE_FORMAT')]").exists());

    mvc.perform(
            patch("/api/v1/rules/DOC_VAT_AMOUNT_MISMATCH")
                .header("Authorization", accountant)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
        .andExpect(status().isForbidden());

    mvc.perform(
            patch("/api/v1/rules/DOC_VAT_AMOUNT_MISMATCH")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{\"nonexistent\":1}}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("RULE_PARAM_INVALID"));

    mvc.perform(
            patch("/api/v1/rules/DOC_VAT_AMOUNT_MISMATCH")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"severity\":\"WARNING\",\"params\":{\"tolerance\":5}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.severity").value("WARNING"))
        .andExpect(jsonPath("$.params.tolerance").value(5))
        .andExpect(jsonPath("$.version").value(2));

    // cấu hình admin không bị mất khi đồng bộ lại; YAML không đổi → unchanged
    var report = syncService.sync();
    assertThat(report.added()).isEmpty();
    assertThat(report.updated()).isEmpty();
    mvc.perform(get("/api/v1/rules/DOC_VAT_AMOUNT_MISMATCH").header("Authorization", admin))
        .andExpect(jsonPath("$.severity").value("WARNING"))
        .andExpect(jsonPath("$.definition.ruleId").value("DOC_VAT_AMOUNT_MISMATCH"));

    mvc.perform(get("/api/v1/violations/" + UUID.randomUUID()).header("Authorization", admin))
        .andExpect(status().isNotFound());
  }
}
