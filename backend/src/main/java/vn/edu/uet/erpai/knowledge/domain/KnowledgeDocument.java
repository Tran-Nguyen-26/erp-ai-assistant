package vn.edu.uet.erpai.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument {

  public enum DocType {
    LAW,
    DECREE,
    CIRCULAR,
    MANUAL,
    INTERNAL
  }

  public enum Status {
    PENDING,
    INGESTING,
    READY,
    FAILED,
    ARCHIVED
  }

  @Id private UUID id;

  @Column(nullable = false)
  private String title;

  @Column(name = "doc_number")
  private String docNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "doc_type", nullable = false)
  private DocType docType;

  @Column(name = "issued_date")
  private LocalDate issuedDate;

  @Column(name = "effective_from")
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Column(name = "source_url")
  private String sourceUrl;

  @Column(name = "file_id")
  private UUID fileId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.PENDING;

  @Column(name = "chunk_count", nullable = false)
  private int chunkCount;

  @Column(name = "embedding_model")
  private String embeddingModel;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected KnowledgeDocument() {}

  public KnowledgeDocument(
      String title,
      String docNumber,
      DocType docType,
      LocalDate issuedDate,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String sourceUrl,
      UUID fileId) {
    this.title = title;
    this.docNumber = docNumber;
    this.docType = docType;
    this.issuedDate = issuedDate;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.sourceUrl = sourceUrl;
    this.fileId = fileId;
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

  /** Cập nhật metadata; ngày hiệu lực có tác dụng ngay với truy xuất, không cần nạp lại. */
  public void updateMetadata(
      String title,
      String docNumber,
      DocType docType,
      LocalDate issuedDate,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String sourceUrl) {
    this.title = title;
    this.docNumber = docNumber;
    this.docType = docType;
    this.issuedDate = issuedDate;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.sourceUrl = sourceUrl;
  }

  public void archive() {
    status = Status.ARCHIVED;
    chunkCount = 0;
  }

  public void markIngesting() {
    status = Status.INGESTING;
    errorMessage = null;
  }

  public void markReady(int chunks, String model) {
    status = Status.READY;
    chunkCount = chunks;
    embeddingModel = model;
    errorMessage = null;
  }

  public void markFailed(String message) {
    status = Status.FAILED;
    errorMessage =
        message == null
            ? "Lỗi không xác định"
            : message.substring(0, Math.min(message.length(), 1000));
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDocNumber() {
    return docNumber;
  }

  public DocType getDocType() {
    return docType;
  }

  public LocalDate getIssuedDate() {
    return issuedDate;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public UUID getFileId() {
    return fileId;
  }

  public Status getStatus() {
    return status;
  }

  public int getChunkCount() {
    return chunkCount;
  }

  public String getEmbeddingModel() {
    return embeddingModel;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
