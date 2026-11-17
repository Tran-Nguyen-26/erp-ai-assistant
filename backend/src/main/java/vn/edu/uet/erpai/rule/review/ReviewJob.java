package vn.edu.uet.erpai.rule.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "review_job")
public class ReviewJob {

  public static final String PENDING = "PENDING";
  public static final String RUNNING = "RUNNING";
  public static final String COMPLETED = "COMPLETED";
  public static final String FAILED = "FAILED";
  public static final String CANCELLED = "CANCELLED";

  @Id private UUID id;

  @Column(name = "company_id", nullable = false)
  private String companyId;

  @Column(name = "period_from", nullable = false)
  private LocalDate periodFrom;

  @Column(name = "period_to", nullable = false)
  private LocalDate periodTo;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> scope;

  @Column(nullable = false)
  private String status = PENDING;

  @Column(name = "total_documents", nullable = false)
  private int totalDocuments;

  @Column(name = "processed_documents", nullable = false)
  private int processedDocuments;

  @Column(name = "total_batches", nullable = false)
  private int totalBatches;

  @Column(name = "failed_batches", nullable = false)
  private int failedBatches;

  @Column(name = "violation_count", nullable = false)
  private int violationCount;

  @Column(name = "requested_by", nullable = false)
  private UUID requestedBy;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected ReviewJob() {}

  public ReviewJob(
      String companyId,
      LocalDate periodFrom,
      LocalDate periodTo,
      Map<String, Object> scope,
      int totalDocuments,
      int totalBatches,
      UUID requestedBy) {
    this.companyId = companyId;
    this.periodFrom = periodFrom;
    this.periodTo = periodTo;
    this.scope = scope;
    this.totalDocuments = totalDocuments;
    this.totalBatches = totalBatches;
    this.requestedBy = requestedBy;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    createdAt = OffsetDateTime.now();
  }

  public void completeImmediately() {
    status = COMPLETED;
    startedAt = OffsetDateTime.now();
    finishedAt = startedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getCompanyId() {
    return companyId;
  }

  public LocalDate getPeriodFrom() {
    return periodFrom;
  }

  public LocalDate getPeriodTo() {
    return periodTo;
  }

  public Map<String, Object> getScope() {
    return scope;
  }

  public String getStatus() {
    return status;
  }

  public int getTotalDocuments() {
    return totalDocuments;
  }

  public int getProcessedDocuments() {
    return processedDocuments;
  }

  public int getTotalBatches() {
    return totalBatches;
  }

  public int getFailedBatches() {
    return failedBatches;
  }

  public int getViolationCount() {
    return violationCount;
  }

  public UUID getRequestedBy() {
    return requestedBy;
  }

  public OffsetDateTime getStartedAt() {
    return startedAt;
  }

  public OffsetDateTime getFinishedAt() {
    return finishedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
