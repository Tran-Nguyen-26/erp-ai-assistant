package vn.edu.uet.erpai.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.uet.erpai.auth.service.AuthService;
import vn.edu.uet.erpai.common.security.Role;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIT extends vn.edu.uet.erpai.AbstractPostgresIT {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired AuthService authService;
  @Autowired JdbcTemplate jdbc;

  @Test
  void loginMeRefreshAndAuditFlow() throws Exception {
    authService.createUser("chief_it", "secret123", "Kế toán trưởng", Role.CHIEF_ACCOUNTANT, "C1");

    var body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"chief_it\",\"password\":\"secret123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.user.role").value("CHIEF_ACCOUNTANT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var json = mapper.readTree(body);

    mvc.perform(
            get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + json.get("accessToken").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("chief_it"))
        .andExpect(jsonPath("$.companyId").value("C1"));

    // refresh token không dùng được như access token
    mvc.perform(
            get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + json.get("refreshToken").asText()))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + json.get("refreshToken").asText() + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'LOGIN_SUCCEEDED'", Integer.class))
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void badCredentialsReturnProblemDetailAndNoTokenMeansUnauthorized() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nobody\",\"password\":\"x\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

    mvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
  }
}
