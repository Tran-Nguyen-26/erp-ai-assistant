package vn.edu.uet.erpai.agent.workflow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.edu.uet.erpai.agent.domain.AgentMessage;
import vn.edu.uet.erpai.agent.domain.AgentSession;
import vn.edu.uet.erpai.agent.session.AgentSessionService;
import vn.edu.uet.erpai.agent.tool.AgentTool;
import vn.edu.uet.erpai.agent.tool.AgentToolRegistry;
import vn.edu.uet.erpai.agent.tool.ToolResult;
import vn.edu.uet.erpai.audit.service.ActorType;
import vn.edu.uet.erpai.audit.service.AuditEvent;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.llm.LlmClient;
import vn.edu.uet.erpai.common.pii.PiiMaskingService;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievedChunk;

/**
 * Chat có tool-calling: vòng lặp quyết định → thực thi công cụ, giới hạn {@code
 * app.agent.max-steps}. Công cụ chỉ đọc (hoặc tạo Proposal đã kiểm chứng) và được lọc theo vai trò
 * TRƯỚC khi đưa vào prompt. Câu trả lời dựa trên tài liệu phải có trích dẫn [n] hợp lệ; thiếu căn
 * cứ thì từ chối (nguyên tắc #4).
 */
@Service
public class ChatWorkflow {

  private static final Logger log = LoggerFactory.getLogger(ChatWorkflow.class);
  private static final int MAX_QUESTION = 4000;
  private static final int MAX_TOOL_CONTENT = 6000;
  private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,3})]");
  static final String NO_EVIDENCE_ANSWER =
      "Không tìm thấy căn cứ đủ tin cậy trong kho tài liệu để trả lời câu hỏi này.";
  static final String UNCITED_NOTE =
      "\n\n⚠ Câu trả lời chưa được dẫn nguồn xác thực từ tài liệu; vui lòng kiểm tra lại.";

  /** Lượt chat đã được kiểm tra hợp lệ và giữ khóa phiên. */
  public record Turn(
      CurrentUser user, AgentSession session, String content, Map<String, Object> context) {}

  private final AgentSessionService sessions;
  private final AgentToolRegistry registry;
  private final LlmClient llm;
  private final PiiMaskingService masking;
  private final ApplicationEventPublisher events;
  private final String provider;
  private final int maxSteps;
  private final int historyWindow;
  private final String decisionPrompt;
  private final String answerPrompt;
  private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

  public ChatWorkflow(
      AgentSessionService sessions,
      AgentToolRegistry registry,
      LlmClient llm,
      PiiMaskingService masking,
      ApplicationEventPublisher events,
      @Value("${app.ai.provider:ollama}") String provider,
      @Value("${app.agent.max-steps:6}") int maxSteps,
      @Value("${app.agent.history-window:10}") int historyWindow) {
    this.sessions = sessions;
    this.registry = registry;
    this.llm = llm;
    this.masking = masking;
    this.events = events;
    this.provider = provider;
    this.maxSteps = maxSteps;
    this.historyWindow = historyWindow;
    this.decisionPrompt = body(load("prompts/agent-chat-system.st"));
    this.answerPrompt = body(load("prompts/agent-chat-answer.st"));
  }

  /** Kiểm tra đầu vào, quyền sở hữu phiên và khóa phiên (mỗi phiên tối đa một lượt đang xử lý). */
  public Turn begin(CurrentUser user, UUID sessionId, String content, Map<String, Object> context) {
    if (content == null || content.isBlank() || content.length() > MAX_QUESTION) {
      throw new BusinessException(
          "ASSISTANT_MESSAGE_INVALID", "Nội dung phải từ 1 đến " + MAX_QUESTION + " ký tự");
    }
    var session = sessions.require(user, sessionId);
    if (!busy.add(sessionId)) {
      throw new BusinessException(
          "ASSISTANT_SESSION_BUSY", "Phiên đang xử lý một câu hỏi khác", HttpStatus.CONFLICT);
    }
    return new Turn(user, session, content.strip(), context == null ? Map.of() : context);
  }

  /** Giải phóng khóa nếu không thể bắt đầu xử lý (ví dụ hàng đợi luồng đầy). */
  public void abort(Turn turn) {
    busy.remove(turn.session().getId());
  }

  /** Chạy lượt chat; luôn giải phóng khóa phiên khi kết thúc. */
  public void execute(Turn turn, ChatEvents out) {
    long start = System.nanoTime();
    var user = turn.user();
    var sessionId = turn.session().getId();
    try {
      var assistantId = UUID.randomUUID();
      var history = sessions.history(sessionId, historyWindow);
      sessions.append(
          AgentMessage.text(sessionId, sessions.nextSeq(sessionId), "USER", turn.content()));
      sessions.touch(sessionId, turn.content());
      out.send("message_start", Map.of("messageId", assistantId, "sessionId", sessionId));

      var state = new TurnState();
      runTools(turn, history, state, out);
      if (out.send("ping", Map.of()) && !state.cancelled) {
        answer(turn, history, state, out, assistantId, start);
      }
    } catch (BusinessException e) {
      out.send("error", Map.of("code", e.getCode(), "message", e.getMessage()));
    } catch (RuntimeException e) {
      log.error("Lượt chat thất bại", e);
      out.send("error", Map.of("code", "INTERNAL_ERROR", "message", "Lỗi hệ thống"));
    } finally {
      busy.remove(sessionId);
    }
  }

  // ---------- vòng lặp công cụ ----------

  private static final class TurnState {
    final List<Scratch> scratch = new ArrayList<>();
    final List<RetrievedChunk> passages = new ArrayList<>();
    final List<String> sensitive = new ArrayList<>();
    final Set<String> seenCalls = new java.util.HashSet<>();
    boolean usedKnowledge;
    boolean knowledgeInsufficient;
    boolean hasDataResult;
    boolean cancelled;
    int toolCalls;
  }

  private record Scratch(String tool, String status, String content) {}

  private void runTools(Turn turn, List<AgentMessage> history, TurnState st, ChatEvents out) {
    var user = turn.user();
    var tools = registry.forRole(user.role());
    var system = decisionPrompt.replace("{{tools}}", describe(tools));
    for (int step = 0; step < maxSteps; step++) {
      var decision = llm.generate(system, prompt(turn, history, st, true), ToolDecision.class);
      if (decision == null || decision.isFinal()) {
        return;
      }
      var args = decision.args() == null ? Map.<String, Object>of() : decision.args();
      var key = decision.tool() + "|" + new java.util.TreeMap<>(args);
      if (!st.seenCalls.add(key)) {
        return; // cùng một lời gọi lặp lại: dừng để tránh vòng lặp
      }
      var tool = registry.find(decision.tool(), user.role());
      if (!out.send("tool_call", Map.of("tool", decision.tool(), "args", args))) {
        st.cancelled = true;
        return;
      }
      var result =
          tool.map(t -> run(t, user, args)).orElseGet(() -> ToolResult.denied(decision.tool()));
      record(turn, decision.tool(), args, result);
      out.send(
          "tool_result",
          Map.of("tool", decision.tool(), "status", result.status(), "summary", result.summary()));
      st.toolCalls++;
      st.sensitive.addAll(result.sensitive());
      absorb(st, decision.tool(), result);
    }
  }

  private ToolResult run(AgentTool tool, CurrentUser user, Map<String, Object> args) {
    try {
      return tool.execute(user, args);
    } catch (BusinessException e) {
      return ToolResult.error(e.getMessage());
    }
  }

  private void absorb(TurnState st, String tool, ToolResult result) {
    var content = result.content();
    if (!result.passages().isEmpty()) {
      st.usedKnowledge = true;
      var sb = new StringBuilder();
      for (var p : result.passages()) {
        if (st.passages.stream().noneMatch(x -> x.chunkId().equals(p.chunkId()))) {
          st.passages.add(p);
        }
      }
      content = sb.append(result.summary()).toString();
    }
    if ("NO_EVIDENCE".equals(result.status())) {
      st.usedKnowledge = true;
      st.knowledgeInsufficient = true;
    } else if ("OK".equals(result.status()) && result.passages().isEmpty()) {
      st.hasDataResult = true;
    }
    st.scratch.add(new Scratch(tool, result.status(), cut(content, MAX_TOOL_CONTENT)));
  }

  private void record(Turn turn, String tool, Map<String, Object> args, ToolResult result) {
    var sessionId = turn.session().getId();
    sessions.append(
        AgentMessage.tool(
            sessionId,
            sessions.nextSeq(sessionId),
            tool,
            new LinkedHashMap<>(args),
            Map.of("status", result.status(), "summary", result.summary())));
    events.publishEvent(
        new AuditEvent(
            ActorType.AGENT,
            turn.user().id().toString(),
            turn.user().companyId(),
            "TOOL_CALLED",
            "tool",
            tool,
            Map.of("status", result.status(), "sessionId", sessionId.toString())));
  }

  // ---------- câu trả lời ----------

  private void answer(
      Turn turn,
      List<AgentMessage> history,
      TurnState st,
      ChatEvents out,
      UUID assistantId,
      long start) {
    var sessionId = turn.session().getId();
    String text;
    if (st.knowledgeInsufficient && !st.hasDataResult && st.passages.isEmpty()) {
      text = NO_EVIDENCE_ANSWER; // không đủ căn cứ: không gọi LLM để tự suy diễn
      out.send("token", Map.of("text", text));
    } else {
      text = generateAnswer(turn, history, st, out);
    }

    var citations = validCitations(text, st.passages);
    if (!st.passages.isEmpty() && citations.isEmpty() && !text.equals(NO_EVIDENCE_ANSWER)) {
      text = text + UNCITED_NOTE;
      out.send("token", Map.of("text", UNCITED_NOTE));
    }
    var citationData = new ArrayList<Map<String, Object>>();
    for (int idx : citations) {
      var c = st.passages.get(idx - 1);
      var m = new LinkedHashMap<String, Object>();
      m.put("index", idx);
      m.put("chunkId", c.chunkId().toString());
      m.put("docNumber", c.docNumber());
      m.put("headingPath", c.headingPath());
      citationData.add(m);
    }
    if (!citationData.isEmpty()) {
      out.send("citations", Map.of("citations", citationData));
    }
    int latency = (int) ((System.nanoTime() - start) / 1_000_000);
    sessions.append(
        AgentMessage.text(sessionId, sessions.nextSeq(sessionId), "ASSISTANT", text)
            .withId(assistantId)
            .withAnswerMeta(citationData, latency, MDC.get("traceId")));
    events.publishEvent(
        new AuditEvent(
            ActorType.AGENT,
            turn.user().id().toString(),
            turn.user().companyId(),
            "CHAT_ANSWERED",
            "agent_session",
            sessionId.toString(),
            Map.of("steps", st.toolCalls, "cited", !citationData.isEmpty())));
    out.send("done", Map.of("messageId", assistantId, "latencyMs", latency, "steps", st.toolCalls));
  }

  private String generateAnswer(
      Turn turn, List<AgentMessage> history, TurnState st, ChatEvents out) {
    var user = prompt(turn, history, st, false);
    PiiMaskingService.Masked masked = null;
    if (!"ollama".equalsIgnoreCase(provider)) {
      masked = masking.mask(user, st.sensitive); // nguyên tắc #3: LLM cloud chỉ nhận dữ liệu đã che
      user = masked.text();
    }
    var buffer = new StringBuilder();
    if (masked == null) {
      llm.stream(
          answerPrompt,
          user,
          token -> {
            buffer.append(token);
            out.send("token", Map.of("text", token));
          });
    } else {
      llm.stream(answerPrompt, user, buffer::append); // cần đủ văn bản để khôi phục token đã che
      var restored = masked.unmask(buffer.toString());
      buffer.setLength(0);
      buffer.append(restored);
      for (int i = 0; i < restored.length(); i += 40) {
        out.send(
            "token", Map.of("text", restored.substring(i, Math.min(restored.length(), i + 40))));
      }
    }
    return buffer.toString();
  }

  /** Chỉ giữ chỉ số [n] thuộc tập đoạn tài liệu đã truy xuất. */
  static List<Integer> validCitations(String answer, List<RetrievedChunk> passages) {
    var out = new ArrayList<Integer>();
    var m = CITATION.matcher(answer);
    while (m.find()) {
      int n = Integer.parseInt(m.group(1));
      if (n >= 1 && n <= passages.size() && !out.contains(n)) {
        out.add(n);
      }
    }
    return out;
  }

  // ---------- dựng prompt (dữ liệu luôn nằm trong delimiter) ----------

  private String prompt(Turn turn, List<AgentMessage> history, TurnState st, boolean decision) {
    var sb = new StringBuilder();
    if (!history.isEmpty()) {
      sb.append("<history>\n");
      for (var h : history) {
        sb.append("USER".equals(h.getRole()) ? "Người dùng: " : "Trợ lý: ")
            .append(cut(h.getContent(), 800))
            .append('\n');
      }
      sb.append("</history>\n");
    }
    sb.append("<question>\n").append(turn.content()).append("\n</question>\n");
    var entityRef = turn.context().get("entityRef");
    if (entityRef != null) {
      sb.append("<context>Chứng từ đang xem: ").append(entityRef).append("</context>\n");
    }
    if (!st.scratch.isEmpty()) {
      sb.append("<tool_results>\n");
      for (var s : st.scratch) {
        sb.append("<tool_result name=\"")
            .append(s.tool())
            .append("\" status=\"")
            .append(s.status())
            .append("\">\n")
            .append(decision ? cut(s.content(), 1500) : s.content())
            .append("\n</tool_result>\n");
      }
      sb.append("</tool_results>\n");
    }
    if (!st.passages.isEmpty()) {
      sb.append("<passages>\n");
      for (int i = 0; i < st.passages.size(); i++) {
        var p = st.passages.get(i);
        sb.append('[')
            .append(i + 1)
            .append("] ")
            .append(p.docNumber() == null ? p.docTitle() : p.docNumber())
            .append(" — ")
            .append(p.headingPath())
            .append(":\n")
            .append(decision ? cut(p.content(), 300) : p.content())
            .append("\n\n");
      }
      sb.append("</passages>\n");
    }
    sb.append(decision ? "Hãy chọn bước tiếp theo (JSON)." : "Hãy trả lời câu hỏi.");
    return sb.toString();
  }

  private static String describe(List<AgentTool> tools) {
    var sb = new StringBuilder();
    tools.forEach(
        t -> sb.append("- ").append(t.name()).append(": ").append(t.description()).append('\n'));
    return sb.toString();
  }

  private static String cut(String s, int max) {
    return s == null ? "" : s.length() <= max ? s : s.substring(0, max) + "…";
  }

  private static String load(String resource) {
    try (var in = new ClassPathResource(resource).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Không đọc được prompt " + resource, e);
    }
  }

  /** Bỏ dòng "# version: ..." đầu file. */
  private static String body(String raw) {
    return raw.substring(raw.indexOf('\n') + 1).strip();
  }
}
