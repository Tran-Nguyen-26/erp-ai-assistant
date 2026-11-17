package vn.edu.uet.erpai.agent.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.edu.uet.erpai.agent.domain.AgentMessage;
import vn.edu.uet.erpai.agent.domain.AgentSession;
import vn.edu.uet.erpai.agent.session.AgentSessionService;
import vn.edu.uet.erpai.agent.workflow.ChatEvents;
import vn.edu.uet.erpai.agent.workflow.ChatWorkflow;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.SecurityUtils;
import vn.edu.uet.erpai.common.web.PageResponse;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

  public record CreateSessionRequest(String channel, String title) {}

  public record SendRequest(
      @NotBlank @Size(max = 4000) String content, Map<String, Object> context) {}

  public record SessionResponse(
      UUID id,
      String channel,
      String title,
      OffsetDateTime createdAt,
      OffsetDateTime lastActivityAt) {
    static SessionResponse from(AgentSession s) {
      return new SessionResponse(
          s.getId(), s.getChannel(), s.getTitle(), s.getCreatedAt(), s.getLastActivityAt());
    }
  }

  public record MessageResponse(
      UUID id,
      int seq,
      String role,
      String content,
      String toolName,
      Map<String, Object> toolArgs,
      Map<String, Object> toolResult,
      List<Map<String, Object>> citations,
      OffsetDateTime createdAt) {
    static MessageResponse from(AgentMessage m) {
      return new MessageResponse(
          m.getId(),
          m.getSeq(),
          m.getRole(),
          m.getContent(),
          m.getToolName(),
          m.getToolArgs(),
          m.getToolResult(),
          m.getCitations(),
          m.getCreatedAt());
    }
  }

  private static final long SSE_TIMEOUT_MS = 180_000L;

  private final AgentSessionService sessions;
  private final ChatWorkflow workflow;
  private final ExecutorService chatExecutor;
  private final com.fasterxml.jackson.databind.ObjectMapper mapper;

  public AssistantController(
      AgentSessionService sessions,
      ChatWorkflow workflow,
      ExecutorService chatExecutor,
      com.fasterxml.jackson.databind.ObjectMapper mapper) {
    this.sessions = sessions;
    this.workflow = workflow;
    this.chatExecutor = chatExecutor;
    this.mapper = mapper;
  }

  @PostMapping("/sessions")
  public ResponseEntity<SessionResponse> create(
      @RequestBody(required = false) CreateSessionRequest req) {
    var s =
        sessions.create(
            SecurityUtils.currentUser(),
            req == null ? null : req.channel(),
            req == null ? null : req.title());
    return ResponseEntity.created(URI.create("/api/v1/assistant/sessions/" + s.getId()))
        .body(SessionResponse.from(s));
  }

  @GetMapping("/sessions")
  public PageResponse<SessionResponse> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    var result =
        sessions.list(
            SecurityUtils.currentUser(),
            PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "lastActivityAt")));
    return new PageResponse<>(
        result.getContent().stream().map(SessionResponse::from).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @GetMapping("/sessions/{id}/messages")
  public PageResponse<MessageResponse> messages(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    var result =
        sessions.messages(
            SecurityUtils.currentUser(),
            id,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
    return new PageResponse<>(
        result.getContent().stream().map(MessageResponse::from).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  /**
   * Gửi câu hỏi và nhận câu trả lời dạng SSE. Lỗi đầu vào/phiên bận trả về HTTP thường (trước khi
   * mở stream).
   */
  @PostMapping(
      value = "/sessions/{id}/messages",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
  public SseEmitter send(@PathVariable UUID id, @Valid @RequestBody SendRequest req) {
    var user = SecurityUtils.currentUser();
    var turn = workflow.begin(user, id, req.content(), req.context());
    var emitter = new SseEmitter(SSE_TIMEOUT_MS);
    var traceId = MDC.get("traceId");
    ChatEvents events =
        new ChatEvents() {
          private volatile boolean closed;

          @Override
          public boolean send(String event, Object data) {
            if (closed) {
              return false;
            }
            try {
              emitter.send(
                  SseEmitter.event()
                      .name(event)
                      .data(mapper.writeValueAsString(data), MediaType.APPLICATION_JSON));
              return true;
            } catch (IOException | IllegalStateException e) {
              closed = true;
              return false;
            }
          }
        };
    try {
      chatExecutor.submit(
          () -> {
            if (traceId != null) {
              MDC.put("traceId", traceId);
            }
            try {
              workflow.execute(turn, events);
            } finally {
              MDC.remove("traceId");
              emitter.complete();
            }
          });
    } catch (RejectedExecutionException e) {
      workflow.abort(turn);
      throw new BusinessException(
          "ASSISTANT_OVERLOADED", "Hệ thống đang bận, thử lại sau", HttpStatus.SERVICE_UNAVAILABLE);
    }
    return emitter;
  }

  @DeleteMapping("/sessions/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    sessions.delete(SecurityUtils.currentUser(), id);
  }
}
