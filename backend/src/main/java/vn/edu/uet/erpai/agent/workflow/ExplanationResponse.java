package vn.edu.uet.erpai.agent.workflow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ExplanationResponse(
    UUID violationId,
    Explanation explanation,
    vn.edu.uet.erpai.review.service.ProposalResponse proposal,
    String proposalSkippedReason) {

  public record Explanation(
      UUID id,
      String summary,
      String explanation,
      List<Citation> citations,
      String supportStatus,
      BigDecimal confidence,
      String model,
      String promptVersion,
      boolean cached,
      OffsetDateTime createdAt) {}

  public record Citation(
      int index,
      UUID chunkId,
      UUID documentId,
      String docNumber,
      String headingPath,
      String quote,
      LocalDate effectiveFrom) {}
}
