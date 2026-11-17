package vn.edu.uet.erpai.agent.workflow;

import java.util.Map;

/**
 * Quyết định mỗi bước của LLM: gọi một công cụ hoặc {@code final}. Mọi trường là dữ liệu không tin
 * cậy.
 */
public record ToolDecision(String tool, Map<String, Object> args) {

  public static final String FINAL = "final";

  public boolean isFinal() {
    return tool == null || tool.isBlank() || FINAL.equalsIgnoreCase(tool.strip());
  }
}
