package vn.edu.uet.erpai.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.uet.erpai.review.service.CreatorType;

@Entity
@Table(name = "proposal")
public class Proposal {

  public enum Status {
    PENDING,
    APPROVED,
    REJECTED,
    SUPERSEDED,
    EXPORTED
  }

  @Id private UUID id;

  @Column(name = "violation_id")
  private UUID violationId;

  @Column(name = "company_id", nullable = false)
  private String companyId;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_ref", nullable = false)
  private String entityRef;

  @Column(name = "action_type", nullable = false)
  private String actionType = "UPDATE_FIELD";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_data", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> beforeData;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_data", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> afterData;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> changes;

  @Column(nullable = false)
  private String rationale;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> citations;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> verification;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "created_by_type", nullable = false)
  private CreatorType createdByType;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "reviewed_at")
  private OffsetDateTime reviewedAt;

  @Column(name = "review_note")
  private String reviewNote;

  @Column(name = "exported_at")
  private OffsetDateTime exportedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected Proposal() {}

  public Proposal(
      UUID violationId,
      String companyId,
      String entityType,
      String entityRef,
      Map<String, Object> beforeData,
      Map<String, Object> afterData,
      List<Map<String, Object>> changes,
      String rationale,
      List<Map<String, Object>> citations,
      Map<String, Object> verification,
      CreatorType createdByType,
      UUID createdBy) {
    this.violationId = violationId;
    this.companyId = companyId;
    this.entityType = entityType;
    this.entityRef = entityRef;
    this.beforeData = beforeData;
    this.afterData = afterData;
    this.changes = changes;
    this.rationale = rationale;
    this.citations = citations;
    this.verification = verification;
    this.createdByType = createdByType;
    this.createdBy = createdBy;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    createdAt = OffsetDateTime.now();
  }

  public void supersede() {
    status = Status.SUPERSEDED;
  }

  public void approve(UUID reviewer, String note) {
    status = Status.APPROVED;
    reviewedBy = reviewer;
    reviewedAt = OffsetDateTime.now();
    reviewNote = note;
  }

  public void reject(UUID reviewer, String note) {
    status = Status.REJECTED;
    reviewedBy = reviewer;
    reviewedAt = OffsetDateTime.now();
    reviewNote = note;
  }

  public void markExported() {
    status = Status.EXPORTED;
    exportedAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getViolationId() {
    return violationId;
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

  public String getActionType() {
    return actionType;
  }

  public Map<String, Object> getBeforeData() {
    return beforeData;
  }

  public Map<String, Object> getAfterData() {
    return afterData;
  }

  public List<Map<String, Object>> getChanges() {
    return changes;
  }

  public String getRationale() {
    return rationale;
  }

  public List<Map<String, Object>> getCitations() {
    return citations;
  }

  public Map<String, Object> getVerification() {
    return verification;
  }

  public Status getStatus() {
    return status;
  }

  public CreatorType getCreatedByType() {
    return createdByType;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public OffsetDateTime getReviewedAt() {
    return reviewedAt;
  }

  public String getReviewNote() {
    return reviewNote;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
