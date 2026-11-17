package vn.edu.uet.erpai.rule.definition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import vn.edu.uet.erpai.erp.model.ErpEntityType;

/** Mô hình rule đọc từ YAML (resources/rules/&lt;nhom&gt;/*.yml). */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RuleDefinition(
    String ruleId,
    String name,
    Group group,
    Severity severity,
    ErpEntityType entityType,
    List<ErpEntityType> entityTypes,
    Evaluator evaluator,
    String forEach,
    Map<String, Object> params,
    String condition,
    String message,
    Map<String, String> evidence,
    String evidenceKey,
    String sqlRef,
    List<String> sqlParams,
    List<String> needs,
    List<String> legalRefs,
    String ragQuery,
    @com.fasterxml.jackson.annotation.JsonProperty("enabled") Boolean enabled,
    Integer version) {

  public enum Group {
    FLD,
    DOC,
    XDOC,
    REG,
    ANM
  }

  public enum Severity {
    ERROR,
    WARNING,
    INFO
  }

  public enum Evaluator {
    SPEL,
    SQL
  }

  public RuleDefinition {
    params = params == null ? Map.of() : Map.copyOf(params);
    evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    entityTypes =
        entityTypes == null || entityTypes.isEmpty()
            ? List.of(entityType)
            : List.copyOf(entityTypes);
    needs = needs == null ? List.of() : List.copyOf(needs);
    sqlParams = sqlParams == null ? List.of() : List.copyOf(sqlParams);
    legalRefs = legalRefs == null ? List.of() : List.copyOf(legalRefs);
    enabled = enabled == null || enabled;
    version = version == null ? 1 : version;
  }

  /** Rule áp dụng cho loại chứng từ này (entityType đơn hoặc danh sách entityTypes). */
  public boolean appliesTo(ErpEntityType type) {
    return entityTypes.contains(type);
  }

  /** Rule cần dữ liệu tham chiếu (ví dụ ACCOUNTS = danh mục tài khoản của công ty). */
  public boolean needs(String reference) {
    return needs.contains(reference);
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public boolean isEnabled() {
    return enabled;
  }

  public RuleDefinition withParams(Map<String, Object> overrides) {
    var merged = new java.util.HashMap<>(params);
    merged.putAll(overrides);
    return new RuleDefinition(
        ruleId,
        name,
        group,
        severity,
        entityType,
        entityTypes,
        evaluator,
        forEach,
        merged,
        condition,
        message,
        evidence,
        evidenceKey,
        sqlRef,
        sqlParams,
        needs,
        legalRefs,
        ragQuery,
        enabled,
        version);
  }

  /** Áp cấu hình do admin chỉnh (severity, bật/tắt, tham số) lên định nghĩa từ YAML. */
  public RuleDefinition withOverrides(
      Severity newSeverity, boolean newEnabled, Map<String, Object> newParams) {
    return new RuleDefinition(
        ruleId,
        name,
        group,
        newSeverity,
        entityType,
        entityTypes,
        evaluator,
        forEach,
        newParams,
        condition,
        message,
        evidence,
        evidenceKey,
        sqlRef,
        sqlParams,
        needs,
        legalRefs,
        ragQuery,
        newEnabled,
        version);
  }
}
