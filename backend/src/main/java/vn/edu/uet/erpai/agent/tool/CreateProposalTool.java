package vn.edu.uet.erpai.agent.tool;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.common.security.Role;
import vn.edu.uet.erpai.review.service.CreatorType;
import vn.edu.uet.erpai.review.service.ProposalService;

/** Chỉ kế toán trưởng/admin. Đề xuất luôn qua kiểm chứng rule; không tự duyệt, không ghi ERP. */
@Component
public class CreateProposalTool implements AgentTool {

  private final ProposalService proposals;

  public CreateProposalTool(ProposalService proposals) {
    this.proposals = proposals;
  }

  @Override
  public String name() {
    return "create_proposal";
  }

  @Override
  public String description() {
    return "Tạo đề xuất sửa một trường cho vi phạm (cần kế toán trưởng duyệt). Input: violationId, path (vd partner.taxCode,"
        + " lines[0].vatAmount), to (giá trị mới), rationale. Output: id đề xuất hoặc lý do bị từ chối kiểm chứng.";
  }

  @Override
  public Set<Role> allowedRoles() {
    return Set.of(Role.CHIEF_ACCOUNTANT, Role.ADMIN);
  }

  @Override
  public ToolResult execute(CurrentUser user, Map<String, Object> args) {
    UUID id;
    try {
      id = UUID.fromString(ToolArgs.string(args, "violationId", true));
    } catch (IllegalArgumentException e) {
      throw new BusinessException("TOOL_ARG_INVALID", "violationId phải là UUID");
    }
    var p =
        proposals.create(
            user,
            id,
            CreatorType.AGENT,
            List.of(
                new ProposalService.ChangeRequest(
                    ToolArgs.string(args, "path", true), ToolArgs.string(args, "to", true))),
            ToolArgs.string(args, "rationale", true),
            List.of());
    return ToolResult.ok(
        "Đã tạo đề xuất " + p.id() + " (chờ duyệt)",
        "Đã tạo đề xuất " + p.id() + ", trạng thái " + p.status() + ".");
  }
}
