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
            ErpEntityType.SALES_INVOICE,
            320,
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
}
