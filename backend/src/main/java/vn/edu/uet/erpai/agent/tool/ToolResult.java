package vn.edu.uet.erpai.agent.tool;

import java.util.List;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievedChunk;

/**
 * @param status OK | NO_EVIDENCE | ERROR | DENIED
 * @param summary tóm tắt ngắn hiển thị cho người dùng (không chứa dữ liệu nhạy cảm)
 * @param content nội dung chi tiết đưa cho LLM (bọc trong delimiter, có thể bị mask)
 * @param passages đoạn tài liệu truy xuất được (để đánh số và trích dẫn), có thể rỗng
 * @param sensitive các chuỗi nhạy cảm (tên đối tác...) cần che khi dùng LLM cloud
 */
public record ToolResult(
    String status,
    String summary,
    String content,
    List<RetrievedChunk> passages,
    List<String> sensitive) {

  public ToolResult(String status, String summary, String content, List<RetrievedChunk> passages) {
    this(status, summary, content, passages, List.of());
  }

  public static ToolResult ok(String summary, String content) {
    return new ToolResult("OK", summary, content, List.of(), List.of());
  }

  public static ToolResult error(String message) {
    return new ToolResult("ERROR", message, message, List.of(), List.of());
  }

  public static ToolResult denied(String tool) {
    return new ToolResult(
        "DENIED",
        "Công cụ " + tool + " không khả dụng",
        "Công cụ không khả dụng cho vai trò hiện tại.",
        List.of(),
        List.of());
  }

  public ToolResult withSensitive(List<String> values) {
    return new ToolResult(status, summary, content, passages, values);
  }
}
