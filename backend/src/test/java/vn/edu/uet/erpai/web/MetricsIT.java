package vn.edu.uet.erpai.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.uet.erpai.AbstractPostgresIT;
import vn.edu.uet.erpai.auth.service.AuthService;
import vn.edu.uet.erpai.common.security.Role;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.service.ErpQueryService;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class MetricsIT extends AbstractPostgresIT {

  private static final String COMPANY = "fcfcfcfc-0000-0000-0000-0000000000f4";
  private static final String REF = "20000000-0000-0000-0000-000000000001";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired AuthService authService;
  @MockitoBean ErpQueryService erp;

  private String login(String user, Role role) throws Exception {
    if (!authService.existsByUsername(user)) {
      authService.createUser(user, "pw", user, role, COMPANY);
    }
    var body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + user + "\",\"password\":\"pw\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + mapper.readTree(body).get("accessToken").asText();
  }

  @Test
  void prometheusEndpointIsAdminOnlyAndExposesRuleTimersWithoutVoucherData() throws Exception {
    var accountant = login("mt_acc", Role.ACCOUNTANT);
    var admin = login("mt_admin", Role.ADMIN);
    var voucher =
        new ErpVoucher(
            REF,
            COMPANY,
            ErpEntityType.PURCHASE_INVOICE,
            210,
            "MH-BI-MAT-1",
            LocalDate.of(2026, 10, 15),
            null,
            "Mua hàng",
            new ErpPartnerRef("p", "N", "Công ty Bí Mật", "12"),
            "VND",
            BigDecimal.ONE,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            true,
            List.of(),
            null);
    doReturn(voucher).when(erp).getVoucher(any(), anyString(), anyString());
    mvc.perform(
            post("/api/v1/reviews/check")
                .header("Authorization", accountant)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityType\":\"PURCHASE_INVOICE\",\"entityRef\":\"" + REF + "\"}"))
        .andExpect(status().isOk());

    mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/prometheus").header("Authorization", accountant))
        .andExpect(status().isForbidden());
    var text =
        mvc.perform(get("/actuator/prometheus").header("Authorization", admin))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(text)
        .contains("rule_evaluation_seconds_count")
        .contains("rule=\"FLD_TAX_CODE_FORMAT\"");
    // không rò dữ liệu chứng từ/đối tác vào nhãn số liệu
    assertThat(text).doesNotContain("MH-BI-MAT-1").doesNotContain("Công ty Bí Mật");
  }
}
