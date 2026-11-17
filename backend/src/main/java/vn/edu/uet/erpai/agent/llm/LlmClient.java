package vn.edu.uet.erpai.agent.llm;

/** Cổng gọi LLM với structured output. Tách khỏi Spring AI để test bằng bản giả. */
public interface LlmClient {

  <T> T generate(String systemPrompt, String userPrompt, Class<T> outputType);

  /** Sinh văn bản dạng stream; mỗi đoạn token được trả qua {@code onToken}. */
  void stream(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onToken);

  String modelName();
}
