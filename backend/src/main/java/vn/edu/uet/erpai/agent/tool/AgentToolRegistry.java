package vn.edu.uet.erpai.agent.tool;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.security.Role;

/**
 * Danh sách công cụ theo vai trò: công cụ không được phép sẽ KHÔNG xuất hiện trong prompt gửi LLM.
 */
@Component
public class AgentToolRegistry {

  private final List<AgentTool> tools;

  public AgentToolRegistry(List<AgentTool> tools) {
    this.tools = tools.stream().sorted(java.util.Comparator.comparing(AgentTool::name)).toList();
  }

  public List<AgentTool> forRole(Role role) {
    return tools.stream()
        .filter(t -> t.allowedRoles().isEmpty() || t.allowedRoles().contains(role))
        .toList();
  }

  public Optional<AgentTool> find(String name, Role role) {
    return forRole(role).stream().filter(t -> t.name().equals(name)).findFirst();
  }
}
