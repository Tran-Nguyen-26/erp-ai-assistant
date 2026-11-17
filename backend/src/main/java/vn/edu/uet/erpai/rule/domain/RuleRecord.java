package vn.edu.uet.erpai.rule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Evaluator;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Group;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Severity;

/** Bản ghi rule trong DB: ảnh chụp định nghĩa YAML + cấu hình admin (enabled/severity/params). */
@Entity
@Table(name = "rule_definition")
public class RuleRecord {

  @Id private UUID id;

  @Column(name = "rule_id", nullable = false, unique = true)
  private String ruleId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "group_code", nullable = false)
  private Group group;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Severity severity;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Evaluator evaluator;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> definition;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> params;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "legal_refs", nullable = false, columnDefinition = "jsonb")
  private List<String> legalRefs;

  @Column(name = "rag_query")
  private String ragQuery;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private int version;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "source_hash", nullable = false, length = 64)
  private String sourceHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected RuleRecord() {}

  public RuleRecord(String ruleId) {
    this.ruleId = ruleId;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    createdAt = OffsetDateTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  /** Sao chép các trường mô tả từ YAML (không đụng tới cấu hình admin). */
  public void applyYaml(
      String name,
      Group group,
      String entityType,
      Evaluator evaluator,
      Map<String, Object> definition,
      List<String> legalRefs,
      String ragQuery,
      String sourceHash) {
    this.name = name;
    this.group = group;
    this.entityType = entityType;
    this.evaluator = evaluator;
    this.definition = definition;
    this.legalRefs = legalRefs;
    this.ragQuery = ragQuery;
    this.sourceHash = sourceHash;
  }

  public void setConfig(Severity severity, boolean enabled, Map<String, Object> params) {
    this.severity = severity;
    this.enabled = enabled;
    this.params = params;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public UUID getId() {
    return id;
  }

  public String getRuleId() {
    return ruleId;
  }

  public String getName() {
    return name;
  }

  public Group getGroup() {
    return group;
  }

  public Severity getSeverity() {
    return severity;
  }

  public String getEntityType() {
    return entityType;
  }

  public Evaluator getEvaluator() {
    return evaluator;
  }

  public Map<String, Object> getDefinition() {
    return definition;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public List<String> getLegalRefs() {
    return legalRefs;
  }

  public String getRagQuery() {
    return ragQuery;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public int getVersion() {
    return version;
  }

  public String getSourceHash() {
    return sourceHash;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
