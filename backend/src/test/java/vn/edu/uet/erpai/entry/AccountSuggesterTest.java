package vn.edu.uet.erpai.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.llm.LlmClient;
import vn.edu.uet.erpai.entry.extractor.ExtractedInvoice;
import vn.edu.uet.erpai.entry.service.AccountSuggester;
import vn.edu.uet.erpai.rule.engine.RuleReferenceData;
import vn.edu.uet.erpai.rule.service.ReferenceDataProvider;

class AccountSuggesterTest {

  private final LlmClient llm = mock(LlmClient.class);
  private final ReferenceDataProvider reference = mock(ReferenceDataProvider.class);
  private static final RuleReferenceData CHART =
      new RuleReferenceData(
          Set.of("1561", "156", "153", "211", "6422", "331", "1111", "1331", "133"),
          Set.of("1561", "153", "211", "6422", "331", "1111", "1331"),
          Map.of(
              "1561",
              "Hàng hóa",
              "153",
              "Công cụ dụng cụ",
              "211",
              "TSCĐ",
              "6422",
              "Chi phí QLDN",
              "331",
              "Phải trả NCC"));

  private static ExtractedInvoice invoice() {
    return new ExtractedInvoice(
        "1",
        "C26TAA",
        "1",
        null,
        "A",
        "0101234568",
        "B",
        null,
        "VND",
        BigDecimal.ONE,
        new BigDecimal("3000000"),
        new BigDecimal("300000"),
        new BigDecimal("3300000"),
        null,
        List.of(
            new ExtractedInvoice.Line(
                1,
                "Bàn phím",
                "Cái",
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal("1000000"),
                BigDecimal.TEN,
                "10%",
                null),
            new ExtractedInvoice.Line(
                2,
                "Dịch vụ vệ sinh",
                "Gói",
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal("2000000"),
                BigDecimal.TEN,
                "10%",
                null)));
  }

  @Test
  void keepsOnlyLlmAccountsThatExistInTheChartAndCapsConfidence() {
    when(reference.forCompany(anyString())).thenReturn(CHART);
    when(llm.generate(anyString(), anyString(), eq(AccountSuggester.Choices.class)))
        .thenReturn(
            new AccountSuggester.Choices(
                List.of(
                    new AccountSuggester.Choice(1, "153", "Công cụ", 0.99),
                    new AccountSuggester.Choice(
                        2, "9999", "bịa", 0.9)))); // không có trong danh mục

    var out = new AccountSuggester(llm, reference, "ollama").suggest("c", invoice());

    assertThat(out).hasSize(3); // 2 dòng + thuế
    assertThat(out.get(0).debitAccount()).isEqualTo("153");
    assertThat(out.get(0).confidence()).isEqualTo(0.7); // trần của AI
    assertThat(out.get(0).reason()).startsWith("AI gợi ý");
    assertThat(out.get(1).debitAccount()).isEqualTo("1561"); // tài khoản bịa bị thay bằng mặc định
    assertThat(out.get(1).confidence()).isEqualTo(0.5);
    assertThat(out.get(2).debitAccount()).isEqualTo("1331");
    assertThat(out).allSatisfy(e -> assertThat(e.creditAccount()).isEqualTo("331"));
  }

  @Test
  void candidatesExcludeCashBankAndVatAccounts() {
    when(reference.forCompany(anyString())).thenReturn(CHART);
    when(llm.generate(anyString(), anyString(), eq(AccountSuggester.Choices.class)))
        .thenReturn(new AccountSuggester.Choices(List.of()));

    new AccountSuggester(llm, reference, "ollama").suggest("c", invoice());

    var user = ArgumentCaptor.forClass(String.class);
    verify(llm).generate(anyString(), user.capture(), eq(AccountSuggester.Choices.class));
    assertThat(user.getValue())
        .contains("1561 - Hàng hóa", "6422")
        .doesNotContain("1111", "1331", "331 -");
  }

  @Test
  void fallsBackToDefaultsWhenLlmFailsChartIsMissingOrProviderIsCloud() {
    when(reference.forCompany(anyString())).thenReturn(CHART);
    when(llm.generate(anyString(), anyString(), any()))
        .thenThrow(new BusinessException("LLM_UNAVAILABLE", "down"));
    assertThat(new AccountSuggester(llm, reference, "ollama").suggest("c", invoice()))
        .extracting("debitAccount")
        .containsExactly("1561", "1561", "1331");

    org.mockito.Mockito.clearInvocations(llm);
    when(reference.forCompany(anyString())).thenReturn(RuleReferenceData.EMPTY);
    assertThat(new AccountSuggester(llm, reference, "ollama").suggest("c", invoice())).hasSize(3);
    verify(llm, never()).generate(anyString(), anyString(), any());

    when(reference.forCompany(anyString())).thenReturn(CHART);
    new AccountSuggester(llm, reference, "openai").suggest("c", invoice());
    verify(llm, never()).generate(anyString(), anyString(), any());
  }
}
