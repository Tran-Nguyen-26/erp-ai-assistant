package vn.edu.uet.erpai.rule.review;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Stream tiến độ job qua SSE: sự kiện {@code progress} khi số chứng từ đã xử lý thay đổi (và mỗi
 * vài giây làm nhịp), rồi {@code completed} hoặc {@code failed} khi job kết thúc.
 */
@Component
public class ReviewJobEvents {

  private static final long POLL_SECONDS = 1;
  private static final long TIMEOUT_MS = 30L * 60 * 1000;

  private final ReviewJobService jobs;
  private final ScheduledExecutorService scheduler;
  private final com.fasterxml.jackson.databind.ObjectMapper mapper;

  public ReviewJobEvents(
      ReviewJobService jobs,
      ScheduledExecutorService jobEventsScheduler,
      com.fasterxml.jackson.databind.ObjectMapper mapper) {
    this.jobs = jobs;
    this.scheduler = jobEventsScheduler;
    this.mapper = mapper;
  }

  /** Gọi sau khi đã kiểm tra job tồn tại và thuộc công ty (404 trả về trước khi mở stream). */
  public SseEmitter stream(String companyId, UUID jobId) {
    var emitter = new SseEmitter(TIMEOUT_MS);
    var future = new AtomicReference<ScheduledFuture<?>>();
    var lastProcessed = new int[] {-1};
    var ticks = new int[] {0};
    Runnable task =
        () -> {
          try {
            var job = jobs.get(companyId, jobId);
            var terminal = isTerminal(job.status());
            if (job.progress().processedDocuments() != lastProcessed[0]
                || ticks[0]++ % 5 == 0
                || terminal) {
              lastProcessed[0] = job.progress().processedDocuments();
              send(emitter, terminal ? name(job.status()) : "progress", job);
            }
            if (terminal) {
              finish(emitter, future);
            }
          } catch (RuntimeException | IOException e) {
            finish(emitter, future);
          }
        };
    future.set(scheduler.scheduleWithFixedDelay(task, 0, POLL_SECONDS, TimeUnit.SECONDS));
    Runnable cleanup = () -> cancel(future);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());
    return emitter;
  }

  private void send(SseEmitter emitter, String event, ReviewJobResponse job) throws IOException {
    var data =
        Map.of(
            "id",
            job.id(),
            "status",
            job.status(),
            "progress",
            job.progress(),
            "violationCount",
            job.violationCount());
    emitter.send(
        SseEmitter.event()
            .name(event)
            .data(mapper.writeValueAsString(data), MediaType.APPLICATION_JSON));
  }

  private static boolean isTerminal(String status) {
    return ReviewJob.COMPLETED.equals(status)
        || ReviewJob.FAILED.equals(status)
        || ReviewJob.CANCELLED.equals(status);
  }

  /** COMPLETED → completed; FAILED/CANCELLED → failed (kèm status trong dữ liệu). */
  private static String name(String status) {
    return ReviewJob.COMPLETED.equals(status) ? "completed" : "failed";
  }

  private static void finish(SseEmitter emitter, AtomicReference<ScheduledFuture<?>> future) {
    cancel(future);
    emitter.complete();
  }

  private static void cancel(AtomicReference<ScheduledFuture<?>> future) {
    var f = future.get();
    if (f != null) {
      f.cancel(false);
    }
  }
}
