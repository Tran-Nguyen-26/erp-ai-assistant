package vn.edu.uet.erpai.rule.review;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Cập nhật tiến độ job bằng MỘT câu UPDATE nguyên tử (nhiều worker chạy song song). Job CANCELLED
 * không bị đổi trạng thái.
 */
@Component
public class ReviewJobProgress {

  private final JdbcTemplate jdbc;

  public ReviewJobProgress(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** PENDING → RUNNING khi batch đầu tiên bắt đầu. */
  public void markRunning(UUID jobId) {
    jdbc.update(
        "UPDATE review_job SET status = 'RUNNING', started_at = now() WHERE id = ? AND status = 'PENDING'",
        jobId);
  }

  public boolean isCancelled(UUID jobId) {
    var status =
        jdbc.query("SELECT status FROM review_job WHERE id = ?", (rs, i) -> rs.getString(1), jobId);
    return status.isEmpty() || ReviewJob.CANCELLED.equals(status.get(0));
  }

  /** Ghi nhận một batch đã xử lý xong (docs chứng từ, violations phát hiện, failed nếu vào DLQ). */
  public void finishBatch(UUID jobId, int docs, int violations, boolean failed) {
    int f = failed ? 1 : 0;
    jdbc.update(
        """
        UPDATE review_job SET
          processed_documents = processed_documents + ?,
          violation_count     = violation_count + ?,
          failed_batches      = failed_batches + ?,
          status = CASE
            WHEN status = 'CANCELLED' THEN status
            WHEN processed_documents + ? >= total_documents THEN
              CASE WHEN failed_batches + ? >= total_batches THEN 'FAILED' ELSE 'COMPLETED' END
            ELSE 'RUNNING' END,
          finished_at = CASE
            WHEN status <> 'CANCELLED' AND processed_documents + ? >= total_documents THEN now()
            ELSE finished_at END
        WHERE id = ?
        """,
        docs,
        violations,
        f,
        docs,
        f,
        docs,
        jobId);
  }
}
