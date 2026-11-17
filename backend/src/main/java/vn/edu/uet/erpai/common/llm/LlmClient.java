package vn.edu.uet.erpai.common.llm;

/** Cổng gọi LLM với structured output. Tách khỏi Spring AI để test bằng bản giả. */
public interface LlmClient {

  <T> T generate(String systemPrompt, String userPrompt, Class<T> outputType);

  /** Ảnh đính kèm cho mô hình đa phương thức. */
  record Image(byte[] data, String mimeType) {}

  /** Structured output từ ảnh (OCR/trích xuất bằng mô hình vision, chỉ dùng với LLM local). */
  <T> T generateWithImages(
      String systemPrompt, String userPrompt, java.util.List<Image> images, Class<T> outputType);

  /** Sinh văn bản dạng stream; mỗi đoạn token được trả qua {@code onToken}. */
  void stream(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onToken);

  String modelName();
}
