package vn.edu.uet.erpai.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    when(erp.getVoucher(any(), anyString(), anyString())).thenReturn(voucher("12345"));
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
    when(erp.getVoucher(any(), anyString(), anyString())).thenReturn(voucher("0101234567"));
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
    when(erp.getVoucher(any(), anyString(), anyString())).thenReturn(voucher("12345"));
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

  @Test
  void adminCanDryRunAnyRuleWithoutPersistingViolations() throws Exception {
    var admin = login("admin_test", Role.ADMIN, COMPANY_A);
    var accountant = login("acc_test", Role.ACCOUNTANT, COMPANY_A);
    var entity =
        "{\"entityType\":\"PURCHASE_INVOICE\",\"entity\":{\"ref\":\"r1\",\"companyId\":\"someone-else\","
            + "\"entityType\":\"PURCHASE_INVOICE\",\"voucherTypeId\":210,\"voucherNo\":\"MH9\","
            + "\"voucherDate\":\"2026-10-15\",\"partner\":{\"id\":\"p\",\"code\":\"N\",\"name\":\"A\",\"taxCode\":\"12\"},"
            + "\"currency\":\"VND\",\"exchangeRate\":\"1\",\"totalAmount\":\"1\",\"totalVatAmount\":\"0\","
            + "\"totalDiscountAmount\":\"0\",\"isRecorded\":true,\"lines\":[]}}";

    mvc.perform(
            post("/api/v1/rules/FLD_TAX_CODE_FORMAT/test")
                .header("Authorization", accountant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(entity))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/rules/FLD_TAX_CODE_FORMAT/test")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(entity))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.violated").value(true))
        .andExpect(
            jsonPath("$.results[0].message").value(org.hamcrest.Matchers.containsString("12")));
    // rule đang tắt vẫn chạy thử được
    mvc.perform(
            post("/api/v1/rules/REG_CASH_PAYMENT_THRESHOLD/test")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(entity))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.violated").value(false));
    mvc.perform(
            post("/api/v1/rules/NO_SUCH_RULE/test")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(entity))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/rules/FLD_TAX_CODE_FORMAT/test")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableEntity());
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM violation WHERE entity_ref = 'r1'", Integer.class))
        .isZero();
  }

  @Test
  void salesInvoiceIsCheckedWithTheSameRulesAndKeepsItsDatedReference() throws Exception {
    var acc = login("acc_sales", Role.ACCOUNTANT, "fafafafa-0000-0000-0000-0000000000f1");
    var ref = "55555555-5555-5555-5555-555555555555@2026-10-20";
    var sales =
        new ErpVoucher(
            ref,
            "fafafafa-0000-0000-0000-0000000000f1",
            ErpEntityType.SALES_INVOICE,
            321,
            "BH77",
            LocalDate.of(2026, 10, 20),
            LocalDate.of(2026, 10, 20),
            "Bán hàng",
            new ErpPartnerRef("c", "KH1", "Khách", "12345"),
            "VND",
            BigDecimal.ONE,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            true,
            List.of(),
            null);
    when(erp.getVoucher(any(), anyString(), anyString())).thenReturn(sales);

    var body =
        mapper.readTree(
            mvc.perform(
                    post("/api/v1/reviews/check")
                        .header("Authorization", acc)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"entityType\":\"SALES_INVOICE\",\"entityRef\":\"" + ref + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    var violation = body.get("violations").get(0);
    org.assertj.core.api.Assertions.assertThat(violation.get("ruleId").asText())
        .isEqualTo("FLD_TAX_CODE_FORMAT");
    org.assertj.core.api.Assertions.assertThat(violation.get("entityType").asText())
        .isEqualTo("SALES_INVOICE");
    org.assertj.core.api.Assertions.assertThat(violation.get("entityRef").asText()).isEqualTo(ref);
    // rule chỉ dành cho hóa đơn mua hàng (VAT, trùng hóa đơn, đơn giá) không chạy trên hóa đơn bán
    // hàng
    org.assertj.core.api.Assertions.assertThat(body.get("violations").findValuesAsText("ruleId"))
        .doesNotContain("XDOC_DUPLICATE_INVOICE");
  }

  @Test
  void severitySortFollowsSeverityOrderNotAlphabet() throws Exception {
    var company = "fbfbfbfb-0000-0000-0000-0000000000f2";
    var acc = login("acc_sort", Role.ACCOUNTANT, company);
    // thiếu số chứng từ (ERROR) + MST sai định dạng (WARNING) trên cùng một chứng từ
    var v =
        new ErpVoucher(
            "77777777-7777-7777-7777-777777777777",
            company,
            ErpEntityType.PURCHASE_INVOICE,
            210,
            " ",
            LocalDate.of(2026, 10, 15),
            LocalDate.of(2026, 10, 15),
            "d",
            new ErpPartnerRef("p", "N", "A", "12"),
            "VND",
            BigDecimal.ONE,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            true,
            List.of(),
            null);
    when(erp.getVoucher(any(), anyString(), anyString())).thenReturn(v);
    mvc.perform(
            post("/api/v1/reviews/check")
                .header("Authorization", acc)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"entityType\":\"PURCHASE_INVOICE\",\"entityRef\":\"77777777-7777-7777-7777-777777777777\"}"))
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/violations?sort=severity,asc").header("Authorization", acc))
        .andExpect(jsonPath("$.items[0].severity").value("ERROR"))
        .andExpect(jsonPath("$.items[1].severity").value("WARNING"));
    mvc.perform(get("/api/v1/violations?sort=severity,desc").header("Authorization", acc))
        .andExpect(jsonPath("$.items[0].severity").value("WARNING"));
  }
}
