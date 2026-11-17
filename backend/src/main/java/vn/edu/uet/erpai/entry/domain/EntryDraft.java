package vn.edu.uet.erpai.entry.domain;

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

@Entity
@Table(name = "entry_draft")
public class EntryDraft {

  public enum SourceType {
    XML,
    IMAGE,
    PDF,
    TEXT
  }

  public enum Status {
    DRAFT,
    VALIDATED,
    CONFIRMED,
    DISCARDED
  }

  @Id private UUID id;

  @Column(name = "company_id", nullable = false)
  private String companyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false)
  private SourceType sourceType;

  @Column(name = "source_file_id")
  private UUID sourceFileId;

  @Column(name = "voucher_type")
  private String voucherType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "extracted_data", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> extractedData;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "field_confidence", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> fieldConfidence;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "suggested_entries", nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> suggestedEntries;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> validation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.DRAFT;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected EntryDraft() {}

  public EntryDraft(
      String companyId,
      SourceType sourceType,
      UUID sourceFileId,
      String voucherType,
      Map<String, Object> extractedData,
      Map<String, Object> fieldConfidence,
      List<Map<String, Object>> suggestedEntries,
      List<Map<String, Object>> validation,
      UUID createdBy) {
    this.companyId = companyId;
    this.sourceType = sourceType;
    this.sourceFileId = sourceFileId;
    this.voucherType = voucherType;
    this.extractedData = extractedData;
    this.fieldConfidence = fieldConfidence;
    this.suggestedEntries = suggestedEntries;
    this.validation = validation;
    this.createdBy = createdBy;
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

  public void update(
      Map<String, Object> extractedData,
      List<Map<String, Object>> suggestedEntries,
      List<Map<String, Object>> validation,
      Status status) {
    this.extractedData = extractedData;
    this.suggestedEntries = suggestedEntries;
    this.validation = validation;
    this.status = status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public UUID getId() {
    return id;
  }

  public String getCompanyId() {
    return companyId;
  }

  public SourceType getSourceType() {
    return sourceType;
  }

  public String getVoucherType() {
    return voucherType;
  }

  public Map<String, Object> getExtractedData() {
    return extractedData;
  }

  public Map<String, Object> getFieldConfidence() {
    return fieldConfidence;
  }

  public List<Map<String, Object>> getSuggestedEntries() {
    return suggestedEntries;
  }

  public List<Map<String, Object>> getValidation() {
    return validation;
  }

  public Status getStatus() {
    return status;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
