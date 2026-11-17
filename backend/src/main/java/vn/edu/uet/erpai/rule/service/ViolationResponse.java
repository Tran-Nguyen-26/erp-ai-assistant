package vn.edu.uet.erpai.rule.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import vn.edu.uet.erpai.rule.domain.Violation;

public record ViolationResponse(
    UUID id,
    String ruleId,
    String ruleName,
    String severity,
    String status,
    String entityType,
    String entityRef,
    String entityNo,
    LocalDate entityDate,
    String message,
    Map<String, Object> evidence,
    boolean hasExplanation,
    UUID pendingProposalId,
    String dismissReason,
    OffsetDateTime detectedAt,
    String fingerprint) {

  public static ViolationResponse from(Violation v) {
    return new ViolationResponse(
        v.getId(),
        v.getRule().getRuleId(),
        v.getRule().getName(),
        v.getSeverity().name(),
        v.getStatus().name(),
        v.getEntityType(),
        v.getEntityRef(),
        v.getEntityNo(),
        v.getEntityDate(),
        v.getMessage(),
        v.getEvidence(),
        false, // cập nhật khi có module agent (violation_explanation)
        null, // cập nhật khi có module review (proposal)
        v.getDismissReason(),
        v.getDetectedAt(),
        v.getFingerprint());
  }
}
