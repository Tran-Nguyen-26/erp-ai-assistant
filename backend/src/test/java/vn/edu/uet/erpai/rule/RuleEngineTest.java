package vn.edu.uet.erpai.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uet.erpai.rule.RuleTestData.line;
import static vn.edu.uet.erpai.rule.RuleTestData.voucher;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.rule.definition.RuleDefinition;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Evaluator;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Group;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Severity;
import vn.edu.uet.erpai.rule.definition.RuleLoader;
import vn.edu.uet.erpai.rule.engine.Fingerprint;
import vn.edu.uet.erpai.rule.engine.RuleEngine;
import vn.edu.uet.erpai.rule.engine.SpelRuleEvaluator;

class RuleEngineTest {

  private final RuleLoader loader = new RuleLoader();
  private final SpelRuleEvaluator spelEvaluator = new SpelRuleEvaluator();
  private final RuleEngine engine =
      new RuleEngine(
          loader,
          spelEvaluator,
          new vn.edu.uet.erpai.rule.engine.SqlRuleEvaluator(
              org.mockito.Mockito.mock(vn.edu.uet.erpai.erp.service.ErpQueryService.class),
              spelEvaluator));

  private RuleDefinition rule(String id) {
    return loader.loadAll().stream().filter(r -> r.ruleId().equals(id)).findFirst().orElseThrow();
  }

  private static RuleDefinition spel(String condition) {
    return new RuleDefinition(
        "DOC_TEST",
        "t",
        Group.DOC,
        Severity.ERROR,
        ErpEntityType.PURCHASE_INVOICE,
        null,
        Evaluator.SPEL,
        null,
        Map.of(),
        condition,
        "msg",
        Map.of(),
        null,
        null,
        null,
        null,
        null,
        null,
        true,
        1);
  }

  // ---------- FLD_TAX_CODE_FORMAT ----------

  @Test
  void taxCodeRuleFlagsMalformedTaxCode() {
    var results = engine.evaluate(voucher("MH1", "12345", List.of()));
    assertThat(results).extracting("ruleId").containsExactly("FLD_TAX_CODE_FORMAT");
    assertThat(results.get(0).message()).contains("12345");
    assertThat(results.get(0).severity()).isEqualTo(Severity.WARNING);
  }

  @Test
  void taxCodeRuleAcceptsValidFormats() {
    for (var ok : List.of("0101234567", "0101234567-001", "001234567890", "")) {
      assertThat(engine.evaluate(voucher("MH1", ok, List.of()))).as("MST %s", ok).isEmpty();
    }
  }

  // ---------- FLD_REQUIRED_FIELDS ----------

  @Test
  void requiredFieldsRuleFlagsBlankVoucherNo() {
    var results = engine.evaluate(voucher(" ", "0101234567", List.of()));
    assertThat(results).extracting("ruleId").containsExactly("FLD_REQUIRED_FIELDS");
  }

  // ---------- DOC_VAT_AMOUNT_MISMATCH (đánh giá trực tiếp vì rule đang tắt trong YAML) ----------

  @Test
  void vatRuleIsDisabledByDefaultUntilRateUnitIsConfirmed() {
    assertThat(rule("DOC_VAT_AMOUNT_MISMATCH").isEnabled()).isFalse();
    var v = voucher("MH1", "0101234567", List.of(line(1, "10000000", "10", "1050000")));
    assertThat(engine.evaluate(v)).isEmpty();
  }

  @Test
  void vatRuleFlagsOnlyTheWrongLineWithEvidenceAndStableFingerprint() {
    var vat = rule("DOC_VAT_AMOUNT_MISMATCH");
    var v =
        voucher(
            "MH1",
            "0101234567",
            List.of(line(1, "10000000", "10", "1000000"), line(2, "10000000", "10", "1050000")));
    var spel = new SpelRuleEvaluator();

    var results = spel.evaluate(vat, v);

    assertThat(results).hasSize(1);
    var r = results.get(0);
    assertThat(r.evidence().get("expected").toString()).isEqualTo("1000000");
    assertThat(r.message()).contains("Dòng 2").contains("1050000").contains("1000000");
    assertThat(r.evidenceKey()).isEqualTo("2");
    assertThat(r.fingerprint())
        .isEqualTo(Fingerprint.of("DOC_VAT_AMOUNT_MISMATCH", "PURCHASE_INVOICE", v.ref(), "2"))
        .hasSize(64);
    assertThat(spel.evaluate(vat, v).get(0).fingerprint()).isEqualTo(r.fingerprint());
  }

  @Test
  void vatRuleAcceptsRoundingWithinTolerance() {
    var v = voucher("MH1", "0101234567", List.of(line(1, "3333333", "10", "333334")));
    assertThat(new SpelRuleEvaluator().evaluate(rule("DOC_VAT_AMOUNT_MISMATCH"), v)).isEmpty();
  }

  @Test
  void vatRuleSkipsNonTaxableRate() {
    var v = voucher("MH1", "0101234567", List.of(line(1, "10000000", "-1", "0")));
    assertThat(new SpelRuleEvaluator().evaluate(rule("DOC_VAT_AMOUNT_MISMATCH"), v)).isEmpty();
  }

  @Test
  void ruleForOtherEntityTypeIsNotApplied() {
    var sales =
        new vn.edu.uet.erpai.erp.model.ErpVoucher(
            "r",
            "c",
            ErpEntityType.JOURNAL_ENTRY,
            700,
            " ",
            null,
            null,
            null,
            null,
            "VND",
            null,
            null,
            null,
            null,
            true,
            List.of(),
            null);
    assertThat(engine.evaluate(sales)).isEmpty();
  }

  // ---------- Bảo mật SpEL ----------

  @Test
  void spelCannotReferenceTypes() {
    var v = voucher("MH1", "0101234567", List.of());
    assertThatThrownBy(
            () ->
                new SpelRuleEvaluator()
                    .evaluate(spel("T(java.lang.Runtime).getRuntime() != null"), v))
        .isInstanceOf(org.springframework.expression.spel.SpelEvaluationException.class)
        .hasMessageContaining("java.lang.Runtime");
  }

  @Test
  void spelCannotCallGetClassOrConstructObjects() {
    var v = voucher("MH1", "0101234567", List.of());
    var evaluator = new SpelRuleEvaluator();
    assertThatThrownBy(() -> evaluator.evaluate(spel("#entity.voucherNo.getClass() != null"), v))
        .isInstanceOf(org.springframework.expression.spel.SpelEvaluationException.class)
        .hasMessageContaining("getClass");
    assertThatThrownBy(() -> evaluator.evaluate(spel("new java.lang.StringBuilder() != null"), v))
        .isInstanceOf(org.springframework.expression.spel.SpelEvaluationException.class)
        .hasMessageContaining("StringBuilder");
  }

  @Test
  void spelCannotWriteProperties() {
    var v = voucher("MH1", "0101234567", List.of());
    assertThatThrownBy(
            () -> new SpelRuleEvaluator().evaluate(spel("(#entity.voucherNo = 'x') != null"), v))
        .isInstanceOf(RuntimeException.class);
  }

  // ---------- Loader ----------

  @Test
  void allBundledRulesLoadWithValidNamingConvention() {
    var rules = loader.loadAll();
    assertThat(rules)
        .extracting(RuleDefinition::ruleId)
        .contains("FLD_TAX_CODE_FORMAT", "FLD_REQUIRED_FIELDS", "DOC_VAT_AMOUNT_MISMATCH");
    assertThat(rules).extracting(RuleDefinition::ruleId).doesNotHaveDuplicates();
  }

  @Test
  void loaderRejectsRuleIdThatBreaksConvention() {
    assertThatThrownBy(() -> loader.loadFrom("classpath*:rules-invalid/*.yml"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ruleId");
  }

  // ---------- rule dùng danh mục tài khoản (ReferenceData) ----------

  private static final vn.edu.uet.erpai.rule.engine.RuleReferenceData CHART =
      new vn.edu.uet.erpai.rule.engine.RuleReferenceData(
          java.util.Set.of("156", "1561", "331", "1331"), java.util.Set.of("1561", "331", "1331"));

  private java.util.List<vn.edu.uet.erpai.rule.engine.RuleResult> evalWithChart(
      String ruleId,
      vn.edu.uet.erpai.erp.model.ErpVoucher v,
      vn.edu.uet.erpai.rule.engine.RuleReferenceData data) {
    return engine.evaluate(v, loader.loadAll(), () -> data).stream()
        .filter(r -> r.ruleId().equals(ruleId))
        .toList();
  }

  @Test
  void summaryAccountIsFlaggedButDetailAccountsAreNot() {
    var bad = voucher("MH1", "0101234567", List.of(line(1, "1000", "10", "100")));
    // line() dùng 1561/331 → hợp lệ
    assertThat(evalWithChart("DOC_ACCOUNT_NOT_DETAIL", bad, CHART)).isEmpty();

    var summary =
        voucher(
            "MH1",
            "0101234567",
            List.of(
                withAccounts(line(1, "1000", "10", "100"), "156", "331"),
                line(2, "1000", "10", "100")));
    var r = evalWithChart("DOC_ACCOUNT_NOT_DETAIL", summary, CHART);
    assertThat(r).hasSize(1);
    assertThat(r.get(0).message()).contains("Dòng 1").contains("156");
    assertThat(r.get(0).evidenceKey()).isEqualTo("1");
  }

  @Test
  void accountMissingFromChartViolatesRegimeRuleOnlyWhenChartIsKnown() {
    var v =
        voucher(
            "MH1",
            "0101234567",
            List.of(withAccounts(line(1, "1000", "10", "100"), "9999", "331")));
    var r = evalWithChart("REG_ACCOUNT_REGIME_MISMATCH", v, CHART);
    assertThat(r).hasSize(1);
    assertThat(r.get(0).message()).contains("9999");

    // chưa nạp được danh mục → không báo sai
    assertThat(
            evalWithChart(
                "REG_ACCOUNT_REGIME_MISMATCH",
                v,
                vn.edu.uet.erpai.rule.engine.RuleReferenceData.EMPTY))
        .isEmpty();
  }

  @Test
  void referenceDataIsOnlyLoadedWhenARuleNeedsIt() {
    var loads = new java.util.concurrent.atomic.AtomicInteger();
    var v = voucher("MH1", "0101234567", List.of(line(1, "1000", "10", "100")));
    engine.evaluate(
        v,
        List.of(rule("FLD_TAX_CODE_FORMAT")),
        () -> {
          loads.incrementAndGet();
          return CHART;
        });
    assertThat(loads).hasValue(0);
    engine.evaluate(
        v,
        loader.loadAll(),
        () -> {
          loads.incrementAndGet();
          return CHART;
        });
    assertThat(loads).hasValue(1); // nhiều rule cần dữ liệu nhưng chỉ nạp một lần
  }

  @Test
  void cashThresholdRuleFlagsOnlyCashInvoicesAtOrAboveThreshold() {
    var rule = rule("REG_CASH_PAYMENT_THRESHOLD");
    assertThat(rule.isEnabled()).isFalse(); // chờ xác nhận ngưỡng với văn bản hiện hành
    var evaluator = new SpelRuleEvaluator();
    assertThat(evaluator.evaluate(rule, withInvoice("CASH", "6000000"))).hasSize(1);
    assertThat(evaluator.evaluate(rule, withInvoice("CASH", "4999999"))).isEmpty();
    assertThat(evaluator.evaluate(rule, withInvoice("BANK_TRANSFER", "90000000"))).isEmpty();
    assertThat(evaluator.evaluate(rule, voucher("MH1", "0101234567", List.of())))
        .isEmpty(); // chưa có hóa đơn
  }

  private static vn.edu.uet.erpai.erp.model.ErpJournalLine withAccounts(
      vn.edu.uet.erpai.erp.model.ErpJournalLine l, String debit, String credit) {
    return new vn.edu.uet.erpai.erp.model.ErpJournalLine(
        l.lineNo(),
        l.description(),
        debit,
        credit,
        l.amount(),
        l.amountOriginal(),
        l.materialGoodsId(),
        l.quantity(),
        l.unitPrice(),
        l.vatRate(),
        l.vatAmount(),
        l.vatAccount());
  }

  private static vn.edu.uet.erpai.erp.model.ErpVoucher withInvoice(String method, String total) {
    var base = voucher("MH1", "0101234567", List.of());
    var amount = new java.math.BigDecimal(total);
    return new vn.edu.uet.erpai.erp.model.ErpVoucher(
        base.ref(),
        base.companyId(),
        base.entityType(),
        base.voucherTypeId(),
        base.voucherNo(),
        base.voucherDate(),
        base.postedDate(),
        base.description(),
        base.partner(),
        "VND",
        java.math.BigDecimal.ONE,
        amount,
        java.math.BigDecimal.ZERO,
        java.math.BigDecimal.ZERO,
        true,
        List.of(),
        new vn.edu.uet.erpai.erp.model.ErpInvoice(
            "1",
            "C26TAA",
            "1",
            base.voucherDate(),
            "0101234567",
            "A",
            amount,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            amount,
            method));
  }
}
