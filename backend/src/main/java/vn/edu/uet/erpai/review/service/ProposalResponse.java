package vn.edu.uet.erpai.review.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.edu.uet.erpai.review.domain.Proposal;
import vn.edu.uet.erpai.rule.service.ViolationResponse;

public record ProposalResponse(
    UUID id,
    String status,
    ViolationRef violation,
    String entityType,
    String entityRef,
    String entityNo,
    String actionType,
    List<Map<String, Object>> changes,
    Map<String, Object> before,
    Map<String, Object> after,
    String rationale,
    List<Map<String, Object>> citations,
    Map<String, Object> verification,
    String createdByType,
    OffsetDateTime createdAt,
    UUID reviewedBy,
    OffsetDateTime reviewedAt,
    String reviewNote) {

  public record ViolationRef(UUID id, String ruleId, String severity) {}

  public static ProposalResponse from(Proposal p, ViolationResponse v) {
    return new ProposalResponse(
        p.getId(),
        p.getStatus().name(),
        new ViolationRef(
            p.getViolationId(), v == null ? null : v.ruleId(), v == null ? null : v.severity()),
        p.getEntityType(),
        p.getEntityRef(),
        v == null ? null : v.entityNo(),
        p.getActionType(),
        p.getChanges(),
        p.getBeforeData(),
        p.getAfterData(),
        p.getRationale(),
        p.getCitations(),
        p.getVerification(),
        p.getCreatedByType().name(),
        p.getCreatedAt(),
        p.getReviewedBy(),
        p.getReviewedAt(),
        p.getReviewNote());
  }
}
