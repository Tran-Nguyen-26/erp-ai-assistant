package vn.edu.uet.erpai.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.uet.erpai.AbstractPostgresIT;
import vn.edu.uet.erpai.auth.service.AuthService;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.Role;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.service.ErpQueryService;

@SpringBootTest
@AutoConfigureMockMvc
class EntryFlowIT extends AbstractPostgresIT {

  private static final String COMPANY = "abababab-0000-0000-0000-00000000000a";
  private static final Path UPLOADS = tempDir();

  private static Path tempDir() {
    try {
      return Files.createTempDirectory("erpai-entry");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry r) {
    r.add("app.storage.dir", UPLOADS::toString);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired AuthService authService;
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

  private ResultActions upload(String token, String name, byte[] content) throws Exception {
    return mvc.perform(
        multipart("/api/v1/entry/drafts")
            .file(new MockMultipartFile("file", name, "application/xml", content))
            .header("Authorization", token));
  }

  private static byte[] fixture(String name) throws Exception {
    return new ClassPathResource("fixtures/einvoice/" + name).getInputStream().readAllBytes();
  }

  private JsonNode body(ResultActions r) throws Exception {
    return mapper.readTree(r.andReturn().getResponse().getContentAsString());
  }

  @Test
  void extractValidateEditConfirmWithDuplicateDetectionAndAccessControl() throws Exception {
    var acc = login("e_acc", Role.ACCOUNTANT);
    var acc2 = login("e_acc2", Role.ACCOUNTANT);
    var chief = login("e_chief", Role.CHIEF_ACCOUNTANT);

    doReturn(Optional.of(new ErpPartnerRef("p1", "NCC001", "Công ty TNHH Alpha", "0101234567")))
        .when(erp)
        .findPartnerByTaxCode(anyString(), anyString());
    // ban đầu: hóa đơn này ĐÃ có trong ERP (trùng)
    doReturn(List.of(Map.of("ref", "dup-1", "voucherNo", "MH00099")))
        .when(erp)
        .queryRule(anyString(), anyMap());

    // ----- tạo nháp từ XML -----
    var created =
        body(upload(acc, "hd.xml", fixture("valid-tt78.xml")).andExpect(status().isCreated()));
    var id = created.get("id").asText();
    assertThat(created.get("status").asText()).isEqualTo("DRAFT");
    assertThat(created.get("invoice").get("invoiceNo").asText()).isEqualTo("0000123");
    assertThat(created.get("invoice").get("totalPayable").asText()).isEqualTo("13000000");
    assertThat(created.get("partnerMatch").get("found").asBoolean()).isTrue();
    assertThat(created.get("partnerMatch").get("code").asText()).isEqualTo("NCC001");
    assertThat(created.get("fieldConfidence").get("invoiceNo").asDouble()).isEqualTo(1.0);
    // 2 dòng hàng + 1 bút toán thuế
    assertThat(created.get("suggestedEntries")).hasSize(3);
    assertThat(created.get("suggestedEntries").get(2).get("debitAccount").asText())
        .isEqualTo("1331");
    // rule XDOC_DUPLICATE_INVOICE phát hiện trùng, chạy trên cùng engine với chứng từ ERP
    assertThat(created.get("validation").findValuesAsText("ruleId"))
        .contains("XDOC_DUPLICATE_INVOICE");
    assertThat(created.get("validation").toString()).contains("MH00099");

    // chưa hợp lệ: không validate/confirm được
    mvc.perform(post("/api/v1/entry/drafts/" + id + "/validate").header("Authorization", acc))
        .andExpect(jsonPath("$.status").value("DRAFT"));
    mvc.perform(post("/api/v1/entry/drafts/" + id + "/confirm").header("Authorization", acc))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ENTRY_NOT_VALIDATED"));

    // ----- phân quyền: kế toán viên khác không thấy, kế toán trưởng thấy -----
    mvc.perform(get("/api/v1/entry/drafts/" + id).header("Authorization", acc2))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/entry/drafts/" + id).header("Authorization", chief))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/entry/drafts").header("Authorization", acc2))
        .andExpect(jsonPath("$.totalItems").value(0));

    // ----- sửa số hóa đơn (không còn trùng) + MST sai định dạng → chỉ cảnh báo, không chặn -----
    doReturn(List.of()).when(erp).queryRule(anyString(), anyMap());
    var edited = (ObjectNode) created.get("invoice").deepCopy();
    edited.put("invoiceNo", "0000124");
    edited.put("sellerTaxCode", "123");
    var req = mapper.createObjectNode();
    req.set("invoice", edited);
    var updated =
        body(
            mvc.perform(
                    put("/api/v1/entry/drafts/" + id)
                        .header("Authorization", acc)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req.toString()))
                .andExpect(status().isOk()));
    assertThat(updated.get("validation").findValuesAsText("ruleId"))
        .doesNotContain("XDOC_DUPLICATE_INVOICE")
        .contains("FLD_TAX_CODE_FORMAT");
    assertThat(updated.get("validation").findValuesAsText("severity")).doesNotContain("ERROR");
    mvc.perform(post("/api/v1/entry/drafts/" + id + "/validate").header("Authorization", acc))
        .andExpect(jsonPath("$.status").value("VALIDATED"));

    // ----- ngay trước khi chốt, hóa đơn trùng lại xuất hiện trong ERP → từ chối và quay về DRAFT
    // -----
    doReturn(List.of(Map.of("ref", "dup-2", "voucherNo", "MH00100")))
        .when(erp)
        .queryRule(anyString(), anyMap());
    mvc.perform(post("/api/v1/entry/drafts/" + id + "/confirm").header("Authorization", acc))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("ENTRY_HAS_ERRORS"));
    mvc.perform(get("/api/v1/entry/drafts/" + id).header("Authorization", acc))
        .andExpect(jsonPath("$.status").value("DRAFT"));

    // ----- hết trùng → xác nhận thành công, sau đó không sửa được nữa -----
    doReturn(List.of()).when(erp).queryRule(anyString(), anyMap());
    mvc.perform(post("/api/v1/entry/drafts/" + id + "/validate").header("Authorization", acc));
    mvc.perform(post("/api/v1/entry/drafts/" + id + "/confirm").header("Authorization", acc))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    mvc.perform(
            put("/api/v1/entry/drafts/" + id)
                .header("Authorization", acc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ENTRY_NOT_EDITABLE"));
    mvc.perform(
            get("/api/v1/entry/drafts/" + id + "/export?format=json").header("Authorization", acc))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invoice.invoiceNo").value("0000124"));
    mvc.perform(
            get("/api/v1/entry/drafts/" + id + "/export?format=xlsx").header("Authorization", acc))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void rejectsXxeBadFormatsAndDegradesGracefullyWhenErpIsDown() throws Exception {
    var acc = login("e_acc3", Role.ACCOUNTANT);

    upload(acc, "xxe.xml", fixture("xxe.xml"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("ENTRY_XML_INVALID"));
    upload(acc, "hd.txt", "x".getBytes())
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("ENTRY_UNSUPPORTED_FORMAT"));
    upload(acc, "empty.xml", new byte[0]).andExpect(status().isUnprocessableEntity());

    // ERP không khả dụng: vẫn tạo được nháp, kèm cảnh báo; rule SPEL vẫn chạy
    doThrow(new BusinessException("ERP_UNAVAILABLE", "down", HttpStatus.BAD_GATEWAY))
        .when(erp)
        .findPartnerByTaxCode(anyString(), anyString());
    doThrow(new BusinessException("ERP_UNAVAILABLE", "down", HttpStatus.BAD_GATEWAY))
        .when(erp)
        .queryRule(anyString(), anyMap());
    var draft =
        body(upload(acc, "hd.xml", fixture("valid-tt78.xml")).andExpect(status().isCreated()));
    assertThat(draft.get("partnerMatch").get("found").isNull()).isTrue();
    assertThat(draft.get("validation").findValuesAsText("ruleId")).contains("ERP_UNAVAILABLE");

    // xóa nháp → 204 và không còn truy cập được
    var id = draft.get("id").asText();
    mvc.perform(delete("/api/v1/entry/drafts/" + id).header("Authorization", acc))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/entry/drafts/" + id).header("Authorization", acc))
        .andExpect(status().isNotFound());
  }
}
