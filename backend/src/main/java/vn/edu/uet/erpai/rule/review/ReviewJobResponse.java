package vn.edu.uet.erpai.rule.review;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ReviewJobResponse(
    UUID id,
    LocalDate periodFrom,
    LocalDate periodTo,
    String status,
    Progress progress,
    int violationCount,
    Map<String, Long> summary,
    UUID requestedBy,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    int totalBatches) {

  public record Progress(
      int totalDocuments, int processedDocuments, double percent, int failedBatches) {}

  public static ReviewJobResponse from(ReviewJob j, Map<String, Long> summary) {
    double percent =
        j.getTotalDocuments() == 0
            ? 100.0
            : Math.round(1000.0 * j.getProcessedDocuments() / j.getTotalDocuments()) / 10.0;
    return new ReviewJobResponse(
        j.getId(),
        j.getPeriodFrom(),
        j.getPeriodTo(),
        j.getStatus(),
        new Progress(
            j.getTotalDocuments(), j.getProcessedDocuments(), percent, j.getFailedBatches()),
        j.getViolationCount(),
        summary,
        j.getRequestedBy(),
        j.getStartedAt(),
        j.getFinishedAt(),
        j.getTotalBatches());
  }
}
