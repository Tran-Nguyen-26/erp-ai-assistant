package vn.edu.uet.erpai.audit.service;

import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.CurrentUser;

@Service
public class FeedbackService {

  public enum TargetType {
    EXPLANATION,
    ANSWER,
    PROPOSAL,
    ENTRY_SUGGESTION
  }

  /**
   * @return true nếu tạo mới (201), false nếu ghi đè (200)
   */
  public record Result(boolean created) {}

  private final JdbcTemplate jdbc;
  private final ApplicationEventPublisher events;

  public FeedbackService(JdbcTemplate jdbc, ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.events = events;
  }

  @Transactional
  public Result submit(
      CurrentUser user, TargetType type, UUID targetId, int rating, String comment) {
    if (rating != 1 && rating != -1) {
      throw new BusinessException("FEEDBACK_RATING_INVALID", "rating phải là 1 hoặc -1");
    }
    int updated =
        jdbc.update(
            "UPDATE feedback SET rating = ?, comment = ? WHERE target_type = ? AND target_id = ? AND user_id = ?",
            rating,
            comment,
            type.name(),
            targetId,
            user.id());
    if (updated == 0) {
      jdbc.update(
          "INSERT INTO feedback (target_type, target_id, user_id, rating, comment) VALUES (?,?,?,?,?)",
          type.name(),
          targetId,
          user.id(),
          rating,
          comment);
    }
    events.publishEvent(
        new AuditEvent(
            ActorType.USER,
            user.id().toString(),
            user.companyId(),
            "FEEDBACK_SUBMITTED",
            type.name(),
            targetId.toString(),
            Map.of("rating", rating)));
    return new Result(updated == 0);
  }
}
