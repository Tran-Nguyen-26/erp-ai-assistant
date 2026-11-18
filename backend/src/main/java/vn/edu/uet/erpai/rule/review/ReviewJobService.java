package vn.edu.uet.erpai.rule.review;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.uet.erpai.audit.service.ActorType;
import vn.edu.uet.erpai.audit.service.AuditEvent;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.rule.definition.RuleDefinition.Group;
import vn.edu.uet.erpai.rule.service.ViolationService;

@Service
public class ReviewJobService {

  public static final int BATCH_SIZE = 200;
  private static final int ID_PAGE = 2000;

  public record CreateRequest(
      LocalDate periodFrom,
      LocalDate periodTo,
      Set<ErpEntityType> entityTypes,
      Set<Group> ruleGroups,
      Set<String> ruleIds) {}

  private final ReviewJobRepository jobs;
  private final ErpQueryService erp;
  private final ReviewBatchProducer producer;
  private final ViolationService violations;
  private final JdbcTemplate jdbc;
  private final ApplicationEventPublisher events;

  public ReviewJobService(
      ReviewJobRepository jobs,
      ErpQueryService erp,
      ReviewBatchProducer producer,
      ViolationService violations,
      JdbcTemplate jdbc,
      ApplicationEventPublisher events) {
    this.jobs = jobs;
    this.erp = erp;
    this.producer = producer;
    this.violations = violations;
    this.jdbc = jdbc;
    this.events = events;
  }

  @Transactional
  public ReviewJobResponse create(CurrentUser user, CreateRequest req) {
    if (req.periodFrom() == null
        || req.periodTo() == null
        || req.periodTo().isBefore(req.periodFrom())) {
      throw new BusinessException("REVIEW_PERIOD_INVALID", "Kỳ rà soát không hợp lệ");
    }
    if (ChronoUnit.MONTHS.between(req.periodFrom(), req.periodTo().plusDays(1)) > 12) {
      throw new BusinessException(
          "REVIEW_PERIOD_TOO_LONG", "Kỳ rà soát không được vượt quá 12 tháng");
    }
    var types =
        req.entityTypes() == null || req.entityTypes().isEmpty()
            ? EnumSet.of(ErpEntityType.PURCHASE_INVOICE)
            : EnumSet.copyOf(req.entityTypes());
    if (!ErpQueryService.SUPPORTED_TYPES.containsAll(types)) {
      throw new BusinessException(
          "RULE_ENTITY_TYPE_UNSUPPORTED",
          "Chỉ rà soát hàng loạt được các loại: " + ErpQueryService.SUPPORTED_TYPES);
    }
    if (jobs.existsByCompanyIdAndPeriodFromAndPeriodToAndStatusIn(
        user.companyId(),
        req.periodFrom(),
        req.periodTo(),
        List.of(ReviewJob.PENDING, ReviewJob.RUNNING))) {
      throw new BusinessException(
          "REVIEW_JOB_ALREADY_RUNNING", "Đã có job đang chạy cho kỳ này", HttpStatus.CONFLICT);
    }

    long total = 0;
    int batches = 0;
    for (var type : types) {
      long n = erp.countVouchers(type, user.companyId(), req.periodFrom(), req.periodTo());
      total += n;
      batches += (int) ((n + BATCH_SIZE - 1) / BATCH_SIZE);
    }
    var scope =
        Map.<String, Object>of(
            "entityTypes", types.stream().map(Enum::name).sorted().toList(),
            "ruleGroups",
                req.ruleGroups() == null
                    ? List.of()
                    : req.ruleGroups().stream().map(Enum::name).sorted().toList(),
            "ruleIds",
                req.ruleIds() == null ? List.of() : req.ruleIds().stream().sorted().toList());
    var job =
        jobs.save(
            new ReviewJob(
                user.companyId(),
                req.periodFrom(),
                req.periodTo(),
                scope,
                (int) total,
                batches,
                user.id()));
    if (total == 0) {
      job.completeImmediately();
    } else {
      var messages = buildBatches(user.companyId(), job.getId(), req, types);
      // chỉ publish sau khi job đã commit để worker luôn thấy được job
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              messages.forEach(producer::publish);
            }
          });
    }
    events.publishEvent(
        new AuditEvent(
            ActorType.USER,
            user.id().toString(),
            user.companyId(),
            "REVIEW_JOB_CREATED",
            "review_job",
            job.getId().toString(),
            Map.of("totalDocuments", total, "batches", batches)));
    return ReviewJobResponse.from(job, Map.of());
  }

  private List<ReviewBatchMessage> buildBatches(
      String companyId, UUID jobId, CreateRequest req, Set<ErpEntityType> types) {
    var messages = new ArrayList<ReviewBatchMessage>();
    int batchNo = 0;
    for (var type : types) {
      var buffer = new ArrayList<String>();
      long offset = 0;
      while (true) {
        var page =
            erp.listVoucherIds(type, companyId, req.periodFrom(), req.periodTo(), offset, ID_PAGE);
        if (page.isEmpty()) break;
        buffer.addAll(page);
        offset += page.size();
        while (buffer.size() >= BATCH_SIZE) {
          messages.add(
              new ReviewBatchMessage(
                  jobId,
                  companyId,
                  type.name(),
                  ++batchNo,
                  List.copyOf(buffer.subList(0, BATCH_SIZE))));
          buffer.subList(0, BATCH_SIZE).clear();
        }
        if (page.size() < ID_PAGE) break;
      }
      if (!buffer.isEmpty()) {
        messages.add(
            new ReviewBatchMessage(jobId, companyId, type.name(), ++batchNo, List.copyOf(buffer)));
      }
    }
    return messages;
  }

  @Transactional(readOnly = true)
  public ReviewJobResponse get(String companyId, UUID id) {
    var job = find(companyId, id);
    return ReviewJobResponse.from(job, violations.severityCountsForJob(id));
  }

  @Transactional(readOnly = true)
  public Page<ReviewJobResponse> list(String companyId, String status, Pageable pageable) {
    var page =
        status == null
            ? jobs.findByCompanyId(companyId, pageable)
            : jobs.findByCompanyIdAndStatus(companyId, status, pageable);
    return page.map(j -> ReviewJobResponse.from(j, Map.of()));
  }

  @Transactional
  public ReviewJobResponse cancel(CurrentUser user, UUID id) {
    // Không nạp entity trước khi UPDATE bằng JDBC (tránh đọc lại trạng thái cũ từ persistence
    // context)
    if (!jobs.existsByIdAndCompanyId(id, user.companyId())) {
      throw new BusinessException(
          "REVIEW_JOB_NOT_FOUND", "Không tìm thấy job", HttpStatus.NOT_FOUND);
    }
    int updated =
        jdbc.update(
            "UPDATE review_job SET status = 'CANCELLED', finished_at = now() WHERE id = ? AND status IN ('PENDING','RUNNING')",
            id);
    if (updated == 0) {
      throw new BusinessException(
          "REVIEW_JOB_NOT_CANCELLABLE", "Job đã kết thúc, không thể hủy", HttpStatus.CONFLICT);
    }
    events.publishEvent(
        new AuditEvent(
            ActorType.USER,
            user.id().toString(),
            user.companyId(),
            "REVIEW_JOB_CANCELLED",
            "review_job",
            id.toString(),
            Map.of()));
    return ReviewJobResponse.from(jobs.findById(id).orElseThrow(), Map.of());
  }

  private ReviewJob find(String companyId, UUID id) {
    return jobs.findByIdAndCompanyId(id, companyId)
        .orElseThrow(
            () ->
                new BusinessException(
                    "REVIEW_JOB_NOT_FOUND", "Không tìm thấy job", HttpStatus.NOT_FOUND));
  }
}
