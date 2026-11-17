package vn.edu.uet.erpai.agent.tool;

import java.util.Map;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.knowledge.service.KnowledgeSearchService;

@Component
public class SearchKnowledgeTool implements AgentTool {

  private final KnowledgeSearchService knowledge;

  public SearchKnowledgeTool(KnowledgeSearchService knowledge) {
    this.knowledge = knowledge;
  }

  @Override
  public String name() {
    return "search_knowledge";
  }

  @Override
  public String description() {
    return "Tra cứu văn bản pháp luật/hướng dẫn kế toán còn hiệu lực. Input: query, asOf (yyyy-MM-dd, tùy chọn). Output: các đoạn tài liệu"
        + " kèm nguồn; nếu không đủ căn cứ sẽ báo NO_EVIDENCE.";
  }

  @Override
  public ToolResult execute(CurrentUser user, Map<String, Object> args) {
    var result =
        knowledge.search(
            ToolArgs.string(args, "query", true), ToolArgs.date(args, "asOf", false), 5);
    if (!result.sufficient()) {
      return new ToolResult(
          "NO_EVIDENCE",
          "Không tìm thấy căn cứ đủ tin cậy trong kho tài liệu",
          "Không tìm thấy căn cứ.",
          java.util.List.of());
    }
    return new ToolResult(
        "OK", result.chunks().size() + " đoạn tài liệu phù hợp", "", result.chunks());
  }
}
