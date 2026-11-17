package vn.edu.uet.erpai.agent.workflow;

import java.util.List;

/** Cấu trúc LLM phải trả về (structured output). Mọi trường đều được backend kiểm tra lại. */
public record ExplanationOutput(
    String summary,
    String explanation,
    List<CitationOut> citations,
    Double confidence,
    SuggestedFix suggestedFix) {

  /** Đề xuất sửa của LLM: chỉ path + giá trị mới; "from" do backend tính, không tin LLM. */
  public record SuggestedFix(List<FixChange> changes) {}

  public record FixChange(String path, String to) {}

  public record CitationOut(Integer index, String quote) {}
}
