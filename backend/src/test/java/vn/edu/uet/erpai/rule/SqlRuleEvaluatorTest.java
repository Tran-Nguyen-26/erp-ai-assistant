package vn.edu.uet.erpai.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpInvoice;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.rule.definition.RuleLoader;
import vn.edu.uet.erpai.rule.engine.SpelRuleEvaluator;
import vn.edu.uet.erpai.rule.engine.SqlRuleEvaluator;

class SqlRuleEvaluatorTest {

  private static final String COMPANY = "11111111-1111-1111-1111-111111111111";
  private static final String REF = "22222222-2222-2222-2222-222222222222";

  private final ErpQueryService erp = mock(ErpQueryService.class);
  private final SqlRuleEvaluator evaluator = new SqlRuleEvaluator(erp, new SpelRuleEvaluator());
  private final vn.edu.uet.erpai.rule.definition.RuleDefinition rule =
      new RuleLoader()
          .loadAll().stream()
              .filter(r -> r.ruleId().equals("XDOC_DUPLICATE_INVOICE"))
              .findFirst()
              .orElseThrow();

  private static ErpVoucher voucher(ErpInvoice invoice) {
    return new ErpVoucher(
        REF,
        COMPANY,
        ErpEntityType.PURCHASE_INVOICE,
        210,
        "MH1",
        LocalDate.of(2026, 10, 15),
        null,
        "d",
        new ErpPartnerRef("p", "N", "A", "0101234567"),
        "VND",
        BigDecimal.ONE,
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        true,
        List.of(),
        invoice);
  }

  private static ErpInvoice invoice(String no) {
    return new ErpInvoice(
        "1",
        "C26TAA",
        no,
        LocalDate.of(2026, 10, 15),
        "0101234567",
        "A",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.TEN,
        null);
  }

  @Test
  void duplicateFoundBecomesErrorViolationWithRenderedMessageAndStableFingerprint() {
    when(erp.queryRule(eq("rule-duplicate-invoice"), anyMap()))
        .thenReturn(List.of(Map.of("ref", "dup-ref", "voucherNo", "MH00099")));

    var results = evaluator.evaluate(rule, voucher(invoice("0000123")));

    assertThat(results).hasSize(1);
    var r = results.get(0);
    assertThat(r.message()).contains("C26TAA-0000123").contains("0101234567").contains("MH00099");
    assertThat(r.evidence()).containsEntry("ref", "dup-ref").containsEntry("matchCount", 1);
    assertThat(r.fingerprint())
        .hasSize(64)
        .isEqualTo(evaluator.evaluate(rule, voucher(invoice("0000123"))).get(0).fingerprint());
    assertThat(evaluator.evaluate(rule, voucher(invoice("0000124"))).get(0).fingerprint())
        .isNotEqualTo(r.fingerprint());
  }

  @Test
  void passesOnlyWhitelistedParametersFromTheVoucher() {
    when(erp.queryRule(any(), anyMap())).thenReturn(List.of());
    evaluator.evaluate(rule, voucher(invoice("0000123")));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
    verify(erp).queryRule(eq("rule-duplicate-invoice"), params.capture());
    assertThat(params.getValue())
        .containsOnlyKeys("companyId", "sellerTaxCode", "invoiceSeries", "invoiceNo", "entityRef")
        .containsEntry("companyId", COMPANY)
        .containsEntry("invoiceNo", "0000123")
        .containsEntry("entityRef", REF);
  }

  @Test
  void noDuplicateOrIncompleteInvoiceMeansNoViolationAndNoQuery() {
    when(erp.queryRule(any(), anyMap())).thenReturn(List.of());
    assertThat(evaluator.evaluate(rule, voucher(invoice("0000123")))).isEmpty();

    org.mockito.Mockito.clearInvocations(erp);
    assertThat(evaluator.evaluate(rule, voucher(null))).isEmpty();
    assertThat(evaluator.evaluate(rule, voucher(invoice(" ")))).isEmpty();
    verify(erp, never()).queryRule(any(), anyMap());
  }

  @Test
  void loaderRejectsSqlRulesWithoutSqlRefOrWithUnsafeParams() {
    assertThatThrownBy(
            () -> new RuleLoader().loadFrom("classpath*:rules-invalid-sql/no-sqlref.yml"))
        .hasMessageContaining("sqlRef");
    assertThatThrownBy(
            () -> new RuleLoader().loadFrom("classpath*:rules-invalid-sql/bad-param.yml"))
        .hasMessageContaining("sqlParams");
  }

  @Test
  void erpServiceRefusesArbitrarySqlRefs() {
    var service =
        new ErpQueryService(
            mock(vn.edu.uet.erpai.erp.adapter.ErpVoucherAdapter.class),
            mock(vn.edu.uet.erpai.erp.adapter.ErpSalesPaymentAdapter.class),
            mock(vn.edu.uet.erpai.erp.adapter.ErpVoucherKindAdapter.class));
    for (var bad :
        new String[] {"../application", "rule-x; DROP", "purchase-invoice-by-id", null, "RULE-X"}) {
      assertThatThrownBy(() -> service.queryRule(bad, Map.of()))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getCode()).isEqualTo("ERP_SQL_REF_INVALID"));
    }
  }

  // ---------- ANM_UNIT_PRICE_OUTLIER: truy vấn theo từng dòng ----------

  @Test
  void perLineRuleQueriesEachEligibleLineAndFlagsOnlyOutliers() {
    var anm =
        new RuleLoader()
            .loadAll().stream()
                .filter(r -> r.ruleId().equals("ANM_UNIT_PRICE_OUTLIER"))
                .findFirst()
                .orElseThrow();
    var lines =
        List.of(
            line(1, "11111111-1111-1111-1111-aaaaaaaaaaaa", "500000"),
            line(2, "11111111-1111-1111-1111-bbbbbbbbbbbb", "9000000"),
            line(3, null, "100"), // không có vật tư: bỏ qua
            line(4, "11111111-1111-1111-1111-cccccccccccc", "0")); // đơn giá 0: bỏ qua
    var v =
        new ErpVoucher(
            REF,
            COMPANY,
            ErpEntityType.PURCHASE_INVOICE,
            210,
            "MH1",
            LocalDate.of(2026, 10, 15),
            null,
            "d",
            new ErpPartnerRef("partner-guid", "N", "A", "0101234567"),
            "VND",
            BigDecimal.ONE,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            true,
            lines,
            null);
    when(erp.queryRule(eq("rule-unit-price-outlier"), anyMap()))
        .thenAnswer(
            inv -> {
              Map<String, Object> p = inv.getArgument(1);
              return "11111111-1111-1111-1111-bbbbbbbbbbbb".equals(p.get("materialGoodsId"))
                  ? List.of(
                      Map.<String, Object>of(
                          "avgPrice",
                          new BigDecimal("1000000"),
                          "stdPrice",
                          new BigDecimal("50000"),
                          "n",
                          8))
                  : List.<Map<String, Object>>of();
            });

    var results = evaluator.evaluate(anm, v);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).message())
        .contains("Dòng 2")
        .contains("9000000")
        .contains("1000000")
        .contains("8 lần");
    assertThat(results.get(0).evidenceKey()).isEqualTo("line2");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
    verify(erp, org.mockito.Mockito.times(2))
        .queryRule(eq("rule-unit-price-outlier"), params.capture());
    assertThat(params.getAllValues().get(0))
        .containsEntry("partnerId", "partner-guid")
        .containsEntry("k", 3)
        .containsEntry("minSamples", 5)
        .containsEntry("sinceDate", java.sql.Date.valueOf(LocalDate.of(2025, 10, 15)));
  }

  private static vn.edu.uet.erpai.erp.model.ErpJournalLine line(
      int no, String material, String price) {
    return new vn.edu.uet.erpai.erp.model.ErpJournalLine(
        no,
        "d",
        "1561",
        "331",
        BigDecimal.TEN,
        BigDecimal.TEN,
        material,
        BigDecimal.ONE,
        new BigDecimal(price),
        BigDecimal.TEN,
        BigDecimal.ONE,
        "1331");
  }

  // ---------- XDOC_PAYMENT_EXCEEDS_DEBT ----------

  @Test
  void paymentToSuppliersIsComparedWithThePayableBalanceOnlyWhenItDebits331() {
    var rule =
        new RuleLoader()
            .loadAll().stream()
                .filter(r -> r.ruleId().equals("XDOC_PAYMENT_EXCEEDS_DEBT"))
                .findFirst()
                .orElseThrow();
    var pay =
        new vn.edu.uet.erpai.erp.model.ErpJournalLine(
            1,
            "Trả NCC",
            "331",
            "1111",
            new BigDecimal("8000000"),
            new BigDecimal("8000000"),
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null);
    var other =
        new vn.edu.uet.erpai.erp.model.ErpJournalLine(
            2,
            "Chi khác",
            "642",
            "1111",
            new BigDecimal("1000000"),
            new BigDecimal("1000000"),
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null);
    var v =
        new ErpVoucher(
            REF,
            COMPANY,
            ErpEntityType.PAYMENT,
            116,
            "PC1",
            LocalDate.of(2026, 10, 20),
            null,
            "d",
            new ErpPartnerRef("partner-guid", "N", "A", "0101234567"),
            "VND",
            BigDecimal.ONE,
            new BigDecimal("9000000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            true,
            List.of(pay, other),
            null);
    when(erp.queryRule(eq("rule-payment-exceeds-debt"), anyMap()))
        .thenReturn(
            List.of(
                Map.of("debt", new BigDecimal("5000000"), "paying", new BigDecimal("8000000"))));

    var results = evaluator.evaluate(rule, v);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).message()).contains("8000000").contains("5000000");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
    verify(erp).queryRule(eq("rule-payment-exceeds-debt"), params.capture());
    assertThat(params.getValue())
        .containsEntry("debtPaymentAmount", new BigDecimal("8000000")) // chỉ cộng các dòng Nợ 331*
        .containsEntry("partnerId", "partner-guid")
        .containsEntry("voucherDate", java.sql.Date.valueOf(LocalDate.of(2026, 10, 20)));

    // không có dòng nào Nợ 331 → không truy vấn
    org.mockito.Mockito.clearInvocations(erp);
    var noDebt = v.withLines(List.of(other), null);
    assertThat(evaluator.evaluate(rule, noDebt)).isEmpty();
    verify(erp, never()).queryRule(any(), anyMap());
    // rule chỉ áp dụng cho phiếu chi/báo Nợ
    assertThat(rule.appliesTo(ErpEntityType.PURCHASE_INVOICE)).isFalse();
    assertThat(rule.appliesTo(ErpEntityType.BANK_DEBIT)).isTrue();
  }
}
