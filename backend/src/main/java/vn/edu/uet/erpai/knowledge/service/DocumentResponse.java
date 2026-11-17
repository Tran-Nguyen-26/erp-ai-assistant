package vn.edu.uet.erpai.knowledge.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import vn.edu.uet.erpai.knowledge.domain.KnowledgeDocument;

public record DocumentResponse(
    UUID id,
    String title,
    String docNumber,
    String docType,
    LocalDate issuedDate,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String sourceUrl,
    String status,
    int chunkCount,
    String embeddingModel,
    String errorMessage,
    OffsetDateTime createdAt) {

  public static DocumentResponse from(KnowledgeDocument d) {
    return new DocumentResponse(
        d.getId(),
        d.getTitle(),
        d.getDocNumber(),
        d.getDocType().name(),
        d.getIssuedDate(),
        d.getEffectiveFrom(),
        d.getEffectiveTo(),
        d.getSourceUrl(),
        d.getStatus().name(),
        d.getChunkCount(),
        d.getEmbeddingModel(),
        d.getErrorMessage(),
        d.getCreatedAt());
  }
}
