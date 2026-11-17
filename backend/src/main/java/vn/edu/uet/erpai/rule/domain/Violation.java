package vn.edu.uet.erpai.rule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Severity;

@Entity
@Table(name = "violation")
public class Violation {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rule_definition_id", nullable = false)
  private RuleRecord rule;

  @Column(name = "rule_version", nullable = false)
  private int ruleVersion;

  @Column(name = "review_job_id")
  private UUID reviewJobId;

  @Column(name = "company_id", nullable = false)
  private String companyId;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_ref", nullable = false)
  private String entityRef;

  @Column(name = "entity_no")
  private String entityNo;

  @Column(name = "entity_date")
  private LocalDate entityDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Severity severity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ViolationStatus status = ViolationStatus.OPEN;

  @Column(nullable = false)
  private String message;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> evidence;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 64, unique = true)
  private String fingerprint;

  @Column(name = "detected_at", nullable = false)
  private OffsetDateTime detectedAt;

  @Column(name = "last_seen_at", nullable = false)
  private OffsetDateTime lastSeenAt;

  @Column(name = "resolved_at")
  private OffsetDateTime resolvedAt;

  @Column(name = "dismissed_by")
  private UUID dismissedBy;

  @Column(name = "dismiss_reason")
  private String dismissReason;

  protected Violation() {}

  public Violation(
      RuleRecord rule,
      UUID reviewJobId,
      String companyId,
      String entityType,
      String entityRef,
      String entityNo,
      LocalDate entityDate,
      Severity severity,
      String message,
      Map<String, Object> evidence,
      String fingerprint) {
    this.rule = rule;
    this.ruleVersion = rule.getVersion();
    this.reviewJobId = reviewJobId;
    this.companyId = companyId;
    this.entityType = entityType;
    this.entityRef = entityRef;
    this.entityNo = entityNo;
    this.entityDate = entityDate;
    this.severity = severity;
    this.message = message;
    this.evidence = evidence;
    this.fingerprint = fingerprint;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    detectedAt = OffsetDateTime.now();
    lastSeenAt = detectedAt;
  }

  /** Phát hiện lại cùng fingerprint. RESOLVED → OPEN; DISMISSED giữ nguyên. */
  public void seenAgain(String message, Map<String, Object> evidence, int ruleVersion) {
    this.message = message;
    this.evidence = evidence;
    this.ruleVersion = ruleVersion;
    this.lastSeenAt = OffsetDateTime.now();
    if (status == ViolationStatus.RESOLVED) {
      status = ViolationStatus.OPEN;
      resolvedAt = null;
    }
  }

  /** OPEN → EXPLAINED sau khi agent sinh giải thích (các trạng thái khác giữ nguyên). */
  public void markExplained() {
    if (status == ViolationStatus.OPEN) {
      status = ViolationStatus.EXPLAINED;
    }
  }

  /** OPEN/EXPLAINED → PROPOSED khi có đề xuất PENDING. */
  public void markProposed() {
    if (status == ViolationStatus.OPEN || status == ViolationStatus.EXPLAINED) {
      status = ViolationStatus.PROPOSED;
    }
  }

  /** PROPOSED → RESOLVED_PENDING_ERP khi đề xuất được duyệt. */
  public void markResolvedPendingErp() {
    if (status == ViolationStatus.PROPOSED) {
      status = ViolationStatus.RESOLVED_PENDING_ERP;
    }
  }

  /** PROPOSED → EXPLAINED khi đề xuất bị từ chối. */
  public void revertToExplained() {
    if (status == ViolationStatus.PROPOSED) {
      status = ViolationStatus.EXPLAINED;
    }
  }

  public void markResolved() {
    status = ViolationStatus.RESOLVED;
    resolvedAt = OffsetDateTime.now();
  }

  public void dismiss(UUID userId, String reason) {
    status = ViolationStatus.DISMISSED;
    dismissedBy = userId;
    dismissReason = reason;
  }

  public void reopen() {
    status = ViolationStatus.OPEN;
    dismissedBy = null;
    dismissReason = null;
    resolvedAt = null;
  }

  public UUID getId() {
    return id;
  }

  public RuleRecord getRule() {
    return rule;
  }

  public String getCompanyId() {
    return companyId;
  }

  public String getEntityType() {
    return entityType;
  }

  public String getEntityRef() {
    return entityRef;
  }

  public String getEntityNo() {
    return entityNo;
  }

  public LocalDate getEntityDate() {
    return entityDate;
  }

  public Severity getSeverity() {
    return severity;
  }

  public ViolationStatus getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public Map<String, Object> getEvidence() {
    return evidence;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public OffsetDateTime getDetectedAt() {
    return detectedAt;
  }

  public String getDismissReason() {
    return dismissReason;
  }
}
