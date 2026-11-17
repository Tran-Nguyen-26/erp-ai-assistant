package vn.edu.uet.erpai.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_message")
public class AgentMessage {

  @Id private UUID id;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @Column(nullable = false)
  private int seq;

  @Column(nullable = false)
  private String role;

  private String content;

  @Column(name = "tool_name")
  private String toolName;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tool_args", columnDefinition = "jsonb")
  private Map<String, Object> toolArgs;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tool_result", columnDefinition = "jsonb")
  private Map<String, Object> toolResult;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> citations = List.of();

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @Column(name = "trace_id")
  private String traceId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected AgentMessage() {}

  public static AgentMessage text(UUID sessionId, int seq, String role, String content) {
    var m = new AgentMessage();
    m.sessionId = sessionId;
    m.seq = seq;
    m.role = role;
    m.content = content;
    return m;
  }

  public static AgentMessage tool(
      UUID sessionId, int seq, String tool, Map<String, Object> args, Map<String, Object> result) {
    var m = text(sessionId, seq, "TOOL", null);
    m.toolName = tool;
    m.toolArgs = args;
    m.toolResult = result;
    return m;
  }

  public AgentMessage withAnswerMeta(
      List<Map<String, Object>> citations, int latencyMs, String traceId) {
    this.citations = citations;
    this.latencyMs = latencyMs;
    this.traceId = traceId;
    return this;
  }

  public AgentMessage withId(UUID id) {
    this.id = id;
    return this;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    createdAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public int getSeq() {
    return seq;
  }

  public String getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public String getToolName() {
    return toolName;
  }

  public Map<String, Object> getToolArgs() {
    return toolArgs;
  }

  public Map<String, Object> getToolResult() {
    return toolResult;
  }

  public List<Map<String, Object>> getCitations() {
    return citations;
  }

  public Integer getLatencyMs() {
    return latencyMs;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
