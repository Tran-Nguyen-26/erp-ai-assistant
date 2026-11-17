package vn.edu.uet.erpai.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "violation_explanation")
public class ViolationExplanation {

  public enum SupportStatus {
    SUPPORTED,
    PARTIAL,
    UNSUPPORTED
  }

  @Id private UUID id;

  @Column(name = "violation_id", nullable = false)
  private UUID violationId;

  @Column(nullable = false)
  private String summary;

  @Column(nullable = false)
  private String explanation;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> citations;

  @Enumerated(EnumType.STRING)
  @Column(name = "support_status", nullable = false)
  private SupportStatus supportStatus;

  private BigDecimal confidence;

  @Column(nullable = false)
  private String model;

  @Column(name = "prompt_version", nullable = false)
  private String promptVersion;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected ViolationExplanation() {}

  public ViolationExplanation(
      UUID violationId,
      String summary,
      String explanation,
      List<Map<String, Object>> citations,
      SupportStatus supportStatus,
      BigDecimal confidence,
      String model,
      String promptVersion,
      Integer latencyMs) {
    this.violationId = violationId;
    this.summary = summary;
    this.explanation = explanation;
    this.citations = citations;
    this.supportStatus = supportStatus;
    this.confidence = confidence;
    this.model = model;
    this.promptVersion = promptVersion;
    this.latencyMs = latencyMs;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    createdAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getViolationId() {
    return violationId;
  }

  public String getSummary() {
    return summary;
  }

  public String getExplanation() {
    return explanation;
  }

  public List<Map<String, Object>> getCitations() {
    return citations;
  }

  public SupportStatus getSupportStatus() {
    return supportStatus;
  }

  public BigDecimal getConfidence() {
    return confidence;
  }

  public String getModel() {
    return model;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
