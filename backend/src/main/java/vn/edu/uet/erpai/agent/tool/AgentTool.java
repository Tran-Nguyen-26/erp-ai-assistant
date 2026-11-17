package vn.edu.uet.erpai.agent.tool;

import java.util.Map;
import java.util.Set;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.common.security.Role;

/**
 * Công cụ agent. Chỉ ĐỌC dữ liệu hoặc tạo Proposal (đã qua kiểm chứng rule). Tham số do LLM sinh ra
 * là dữ liệu không tin cậy: mỗi công cụ tự validate và giới hạn.
 */
public interface AgentTool {

  String name();

  /** Mô tả ngắn nêu rõ input/output, đưa vào prompt. */
  String description();

  /** Vai trò được phép dùng; rỗng = mọi vai trò. */
  default Set<Role> allowedRoles() {
    return Set.of();
  }

  ToolResult execute(CurrentUser user, Map<String, Object> args);
}
