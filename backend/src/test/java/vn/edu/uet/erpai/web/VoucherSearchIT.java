package vn.edu.uet.erpai.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.uet.erpai.AbstractPostgresIT;
import vn.edu.uet.erpai.auth.service.AuthService;
import vn.edu.uet.erpai.common.security.Role;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpPage;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.model.VoucherFilter;
import vn.edu.uet.erpai.erp.service.ErpQueryService;

@SpringBootTest
@AutoConfigureMockMvc
class VoucherSearchIT extends AbstractPostgresIT {

  private static final String COMPANY = "fcfcfcfc-0000-0000-0000-0000000000f3";
  private static final String R1 = "10000000-0000-0000-0000-000000000001"; // ERROR + WARNING
  private static final String R2 = "10000000-0000-0000-0000-000000000002"; // WARNING
  private static final String R3 = "10000000-0000-0000-0000-000000000003"; // sạch

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired AuthService authService;
  @MockitoBean ErpQueryService erp;

  private static ErpVoucher voucher(String ref, String no, String taxCode) {
    return new ErpVoucher(
        ref,
        COMPANY,
        ErpEntityType.PURCHASE_INVOICE,
        210,
        no,
        LocalDate.of(2026, 10, 15),
        LocalDate.of(2026, 10, 15),
        "Mua " + no,
        new ErpPartnerRef("p", "N", "Công ty " + no, taxCode),
        "VND",
        BigDecimal.ONE,
        new BigDecimal("1000000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        true,
        List.of(),
        null);
  }

  private String login() throws Exception {
    if (!authService.existsByUsername("vs_acc")) {
      authService.createUser("vs_acc", "pw", "vs", Role.ACCOUNTANT, COMPANY);
    }
    var body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"vs_acc\",\"password\":\"pw\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + mapper.readTree(body).get("accessToken").asText();
  }

  private JsonNode search(String token, String query) throws Exception {
    return mapper.readTree(
        mvc.perform(
                get("/api/v1/erp/vouchers?periodFrom=2026-10-01&periodTo=2026-10-31" + query)
                    .header("Authorization", token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void listShowsOpenViolationCountsAndWorstSeverityAndForwardsFilters() throws Exception {
    var token = login();
    var vouchers =
        Map.of(
            R1,
            voucher(R1, "MH1", "12"),
            R2,
            voucher(R2, "MH2", "0101234567"),
            R3,
            voucher(R3, "MH3", "0101234568"));
    doAnswer(inv -> vouchers.get((String) inv.getArgument(2)))
        .when(erp)
        .getVoucher(any(), anyString(), anyString());
    // R1: thiếu số chứng từ (ERROR) + MST sai (WARNING); R2: chỉ thiếu số chứng từ → dùng số rỗng
    var blank = voucher(R2, " ", "0101234567"); // ERROR
    doAnswer(
            inv ->
                inv.getArgument(2).equals(R2) ? blank : vouchers.get((String) inv.getArgument(2)))
        .when(erp)
        .getVoucher(any(), anyString(), anyString());
    for (var ref : List.of(R1, R2, R3)) {
      mvc.perform(
              post("/api/v1/reviews/check")
                  .header("Authorization", token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"entityType\":\"PURCHASE_INVOICE\",\"entityRef\":\"" + ref + "\"}"))
          .andExpect(status().isOk());
    }
    // R1: MST "12" sai → WARNING; R1 còn số chứng từ nên không ERROR. Đặt lại: R1 có WARNING, R2 có
    // ERROR, R3 sạch
    doReturn(new ErpPage<>(List.of(vouchers.get(R1), blank, vouchers.get(R3)), 0, 20, 3))
        .when(erp)
        .listVouchers(any(), anyString(), any(), any(), anyInt(), anyInt(), any());

    var body = search(token, "&q=MH&partnerTaxCode=0101234567");

    assertThat(body.get("totalItems").asLong()).isEqualTo(3);
    var items = body.get("items");
    assertThat(items.get(0).get("entityRef").asText()).isEqualTo(R1);
    assertThat(items.get(0).get("openViolationCount").asInt()).isEqualTo(1);
    assertThat(items.get(0).get("maxSeverity").asText()).isEqualTo("WARNING");
    assertThat(items.get(1).get("maxSeverity").asText()).isEqualTo("ERROR");
    assertThat(items.get(2).get("openViolationCount").asInt()).isZero();
    assertThat(items.get(2).get("maxSeverity").isNull()).isTrue();
    assertThat(items.get(0).get("totalAmount").asText()).isEqualTo("1000000.00");

    var filter = ArgumentCaptor.forClass(VoucherFilter.class);
    verify(erp)
        .listVouchers(any(), anyString(), any(), any(), anyInt(), anyInt(), filter.capture());
    assertThat(filter.getValue()).isEqualTo(new VoucherFilter("MH", "0101234567"));
  }

  @Test
  void hasViolationsReturnsOnlyVouchersWithOpenViolationsFromTheViolationSide() throws Exception {
    var token = login();
    var v =
        voucher(
            R1, "MH1", "12"); // MST sai → có vi phạm (đã được tạo ở test kia hoặc tạo lại ở đây)
    doReturn(v).when(erp).getVoucher(any(), anyString(), anyString());
    mvc.perform(
            post("/api/v1/reviews/check")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityType\":\"PURCHASE_INVOICE\",\"entityRef\":\"" + R1 + "\"}"))
        .andExpect(status().isOk());

    var body = search(token, "&hasViolations=true");

    assertThat(body.get("totalItems").asLong()).isGreaterThanOrEqualTo(1);
    assertThat(body.get("items").findValuesAsText("entityRef")).contains(R1).doesNotContain(R3);
    assertThat(body.get("items").get(0).get("openViolationCount").asInt())
        .isGreaterThanOrEqualTo(1);
    // chế độ này không truy vấn danh sách ERP
    org.mockito.Mockito.verify(erp, org.mockito.Mockito.never())
        .listVouchers(any(), anyString(), any(), any(), anyInt(), anyInt(), any());

    // lọc trên tập kết quả: MST khác → rỗng
    assertThat(search(token, "&hasViolations=true&partnerTaxCode=9999999999").get("items"))
        .isEmpty();
    // loại chưa hỗ trợ
    mvc.perform(
            get("/api/v1/erp/vouchers?periodFrom=2026-10-01&periodTo=2026-10-31&voucherType=JOURNAL_ENTRY")
                .header("Authorization", token))
        .andExpect(status().isUnprocessableEntity());
  }
}
