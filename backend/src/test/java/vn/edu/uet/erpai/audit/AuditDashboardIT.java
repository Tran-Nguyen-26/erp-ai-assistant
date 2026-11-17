package vn.edu.uet.erpai.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.uet.erpai.AbstractPostgresIT;
import vn.edu.uet.erpai.auth.service.AuthService;
import vn.edu.uet.erpai.common.security.Role;

@SpringBootTest
@AutoConfigureMockMvc
class AuditDashboardIT extends AbstractPostgresIT {

  private static final String COMPANY = "ffffffff-0000-0000-0000-000000000006";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired AuthService authService;
  @Autowired JdbcTemplate jdbc;

  private String login(String username, Role role, String company) throws Exception {
    if (!authService.existsByUsername(username)) {
      authService.createUser(username, "pw", username, role, company);
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

  @Test
  void auditLogsAreRoleRestrictedCompanyScopedAndFilterable() throws Exception {
    var chief = login("a_chief", Role.CHIEF_ACCOUNTANT, COMPANY);
    var acc = login("a_acc", Role.ACCOUNTANT, COMPANY);
    var other = login("a_other", Role.CHIEF_ACCOUNTANT, "99999999-0000-0000-0000-000000000009");

    mvc.perform(get("/api/v1/audit-logs").header("Authorization", acc))
        .andExpect(status().isForbidden());

    mvc.perform(
            get("/api/v1/audit-logs?action=LOGIN_SUCCEEDED&actorType=USER")
                .header("Authorization", chief))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].action").value("LOGIN_SUCCEEDED"))
        .andExpect(jsonPath("$.items[?(@.actor.username=='a_chief')]").exists());

    // công ty khác không thấy log của công ty này
    var otherBody =
        mapper.readTree(
            mvc.perform(
                    get("/api/v1/audit-logs?action=LOGIN_SUCCEEDED").header("Authorization", other))
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(otherBody.get("items").findValuesAsText("username"))
        .doesNotContain("a_chief", "a_acc");
  }

  @Test
  void feedbackUpsertsAndDashboardAggregatesPerCompany() throws Exception {
    var acc = login("d_acc", Role.ACCOUNTANT, COMPANY);
    var target = UUID.randomUUID();
    var body =
        "{\"targetType\":\"EXPLANATION\",\"targetId\":\""
            + target
            + "\",\"rating\":1,\"comment\":\"tốt\"}";

    mvc.perform(
            post("/api/v1/feedback")
                .header("Authorization", acc)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/feedback")
                .header("Authorization", acc)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"rating\":1", "\"rating\":-1")))
        .andExpect(status().isOk()); // ghi đè, không tạo thêm
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM feedback WHERE target_id = ?", Integer.class, target))
        .isEqualTo(1);
    mvc.perform(
            post("/api/v1/feedback")
                .header("Authorization", acc)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"rating\":1", "\"rating\":5")))
        .andExpect(status().isUnprocessableEntity());

    // dữ liệu mẫu: 2 violation ERROR/WARNING của công ty
    var ruleId =
        jdbc.queryForObject(
            "SELECT id FROM rule_definition WHERE rule_id = 'FLD_TAX_CODE_FORMAT'", UUID.class);
    for (var sev : new String[] {"ERROR", "WARNING"}) {
      jdbc.update(
          "INSERT INTO violation (rule_definition_id, rule_version, company_id, entity_type, entity_ref, severity,"
              + " status, message, evidence, fingerprint) VALUES (?,?,?,?,?,?,?,?,?::jsonb,?)",
          ruleId,
          1,
          COMPANY,
          "PURCHASE_INVOICE",
          "r-" + sev,
          sev,
          "OPEN",
          "m",
          "{}",
          sev.repeat(10).substring(0, 1)
              + UUID.randomUUID().toString().replace("-", "")
              + "0".repeat(31));
    }
    mvc.perform(get("/api/v1/dashboard/stats").header("Authorization", acc))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.violations.total").value(2))
        .andExpect(jsonPath("$.violations.open").value(2))
        .andExpect(jsonPath("$.violations.bySeverity.ERROR").value(1))
        .andExpect(jsonPath("$.topRules[0].ruleId").value("FLD_TAX_CODE_FORMAT"))
        .andExpect(jsonPath("$.ai.positiveFeedbackRate").value(0.0));

    var otherCompany = login("d_other", Role.ACCOUNTANT, "88888888-0000-0000-0000-000000000008");
    mvc.perform(get("/api/v1/dashboard/stats").header("Authorization", otherCompany))
        .andExpect(jsonPath("$.violations.total").value(0));
  }
}
