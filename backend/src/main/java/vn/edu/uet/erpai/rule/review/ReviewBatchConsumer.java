package vn.edu.uet.erpai.rule.review;

import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.config.AmqpConfig;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Group;
import vn.edu.uet.erpai.rule.service.RuleCheckService;

/**
 * Worker rà soát. Batch lỗi được retry 3 lần (cấu hình spring.rabbitmq.listener.simple.retry) rồi
 * vào DLQ. Xử lý idempotent: violation upsert theo fingerprint nên chạy lại không tạo trùng.
 */
@Component
public class ReviewBatchConsumer {

  private static final Logger log = LoggerFactory.getLogger(ReviewBatchConsumer.class);

  private final ReviewJobRepository jobs;
  private final ReviewJobProgress progress;
  private final ErpQueryService erp;
  private final RuleCheckService checker;

  public ReviewBatchConsumer(
      ReviewJobRepository jobs,
      ReviewJobProgress progress,
      ErpQueryService erp,
      RuleCheckService checker) {
    this.jobs = jobs;
    this.progress = progress;
    this.erp = erp;
    this.checker = checker;
  }

  @RabbitListener(queues = AmqpConfig.BATCH_QUEUE)
  @SuppressWarnings("unchecked")
  public void onBatch(ReviewBatchMessage msg) {
    if (progress.isCancelled(msg.jobId())) {
      log.info("Bỏ qua batch {} của job đã hủy {}", msg.batchNo(), msg.jobId());
      return;
    }
    progress.markRunning(msg.jobId());
    var job = jobs.findById(msg.jobId()).orElseThrow();
    Set<Group> groups =
        ((java.util.List<String>) job.getScope().getOrDefault("ruleGroups", java.util.List.of()))
            .stream().map(Group::valueOf).collect(Collectors.toSet());
    Set<String> ruleIds =
        Set.copyOf(
            (java.util.List<String>) job.getScope().getOrDefault("ruleIds", java.util.List.of()));

    int violations = 0;
    for (var ref : msg.entityRefs()) {
      if (progress.isCancelled(msg.jobId())) {
        return;
      }
      try {
        var voucher =
            erp.getVoucher(
                vn.edu.uet.erpai.erp.model.ErpEntityType.valueOf(msg.entityType()),
                msg.companyId(),
                ref);
        violations += checker.checkAndStore(msg.companyId(), voucher, msg.jobId(), groups, ruleIds);
      } catch (BusinessException e) {
        if (!"ERP_VOUCHER_NOT_FOUND".equals(e.getCode())) {
          throw e; // lỗi khác → retry/DLQ
        }
        // chứng từ đã bị xóa giữa chừng: bỏ qua
      }
    }
    progress.finishBatch(msg.jobId(), msg.entityRefs().size(), violations, false);
  }

  /** Batch đã hết số lần retry: ghi nhận lỗi để job vẫn kết thúc được. */
  @RabbitListener(queues = AmqpConfig.DLQ)
  public void onDeadLetter(ReviewBatchMessage msg) {
    log.error("Batch {} của job {} vào DLQ", msg.batchNo(), msg.jobId());
    progress.finishBatch(msg.jobId(), msg.entityRefs().size(), 0, true);
  }
}
