package vn.edu.uet.erpai.agent.web;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.edu.uet.erpai.agent.workflow.ExplainViolationWorkflow;
import vn.edu.uet.erpai.agent.workflow.ExplanationResponse;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.common.security.SecurityUtils;

@RestController
@RequestMapping("/api/v1/violations")
public class AgentController {

  private static final long SSE_TIMEOUT_MS = 180_000L;

  private final ExplainViolationWorkflow workflow;
  private final ExecutorService chatExecutor;
  private final com.fasterxml.jackson.databind.ObjectMapper mapper;

  public AgentController(
      ExplainViolationWorkflow workflow,
      ExecutorService chatExecutor,
      com.fasterxml.jackson.databind.ObjectMapper mapper) {
    this.workflow = workflow;
    this.chatExecutor = chatExecutor;
    this.mapper = mapper;
  }

  /**
   * Chế độ JSON đồng bộ ({@code Accept: application/json}). withProposal=true: sinh đề xuất sửa nếu
   * LLM gợi ý và vượt kiểm chứng rule.
   */
  // params="!sse": khi client không gửi Accept (hoặc */*) mapping JSON luôn thắng; ?sse=1 ép chế độ
  // stream
  @PostMapping(
      value = "/{id}/explain",
      produces = MediaType.APPLICATION_JSON_VALUE,
      params = "!sse")
  public ExplanationResponse explain(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "false") boolean regenerate,
      @RequestParam(defaultValue = "true") boolean withProposal) {
    return workflow.explain(SecurityUtils.currentUser(), id, regenerate, withProposal);
  }

  /**
   * Chế độ stream ({@code Accept: text/event-stream}): sự kiện {@code stage} (retrieving,
   * generating, verifying), rồi {@code result} hoặc {@code error}. Đầu ra của LLM là JSON có cấu
   * trúc nên kết quả được gửi một lần ở cuối thay vì từng token.
   */
  @PostMapping(
      value = "/{id}/explain",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
  public SseEmitter explainStream(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "false") boolean regenerate,
      @RequestParam(defaultValue = "true") boolean withProposal) {
    CurrentUser user = SecurityUtils.currentUser();
    var emitter = new SseEmitter(SSE_TIMEOUT_MS);
    var traceId = MDC.get("traceId");
    try {
      chatExecutor.submit(
          () -> {
            if (traceId != null) {
              MDC.put("traceId", traceId);
            }
            try {
              var result =
                  workflow.explain(
                      user,
                      id,
                      regenerate,
                      withProposal,
                      stage -> send(emitter, "stage", Map.of("stage", stage)));
              send(emitter, "result", result);
            } catch (BusinessException e) {
              send(emitter, "error", Map.of("code", e.getCode(), "message", e.getMessage()));
            } catch (RuntimeException e) {
              send(emitter, "error", Map.of("code", "INTERNAL_ERROR", "message", "Lỗi hệ thống"));
            } finally {
              MDC.remove("traceId");
              emitter.complete();
            }
          });
    } catch (RejectedExecutionException e) {
      throw new BusinessException(
          "ASSISTANT_OVERLOADED", "Hệ thống đang bận, thử lại sau", HttpStatus.SERVICE_UNAVAILABLE);
    }
    return emitter;
  }

  private void send(SseEmitter emitter, String event, Object data) {
    try {
      emitter.send(
          SseEmitter.event()
              .name(event)
              .data(mapper.writeValueAsString(data), MediaType.APPLICATION_JSON));
    } catch (IOException | IllegalStateException e) {
      // client đã ngắt kết nối: bỏ qua, quy trình vẫn lưu kết quả
    }
  }
}
