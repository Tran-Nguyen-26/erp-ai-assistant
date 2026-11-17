package vn.edu.uet.erpai.agent.workflow;

/** Đích nhận sự kiện của một lượt chat (SSE). */
public interface ChatEvents {

  /**
   * @return false nếu không gửi được (client đã ngắt kết nối)
   */
  boolean send(String event, Object data);
}
