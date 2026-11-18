package vn.edu.uet.erpai.common.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.metrics.AppMetrics;

@Component
public class SpringAiLlmClient implements LlmClient {

  private final ChatClient chat;
  private final String modelName;
  private final String visionModel;
  private final AppMetrics metrics;

  public SpringAiLlmClient(
      ChatModel chatModel,
      @Value("${spring.ai.ollama.chat.options.model:qwen2.5:7b}") String modelName,
      @Value("${app.ai.vision-model:qwen2.5vl:7b}") String visionModel,
      AppMetrics metrics) {
    this.metrics = metrics;
    this.chat = ChatClient.builder(chatModel).build();
    this.modelName = modelName;
    this.visionModel = visionModel;
  }

  @Override
  public <T> T generate(String systemPrompt, String userPrompt, Class<T> outputType) {
    return metrics.time(
        "llm.call", () -> doGenerate(systemPrompt, userPrompt, outputType), "kind", "text");
  }

  private <T> T doGenerate(String systemPrompt, String userPrompt, Class<T> outputType) {
    try {
      return chat.prompt().system(systemPrompt).user(userPrompt).call().entity(outputType);
    } catch (ResourceAccessException e) {
      boolean timeout = e.getCause() instanceof java.net.SocketTimeoutException;
      throw new BusinessException(
          timeout ? "LLM_TIMEOUT" : "LLM_UNAVAILABLE",
          timeout ? "LLM không phản hồi kịp" : "LLM không khả dụng",
          timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY);
    } catch (RuntimeException e) {
      throw new BusinessException(
          "LLM_UNAVAILABLE", "LLM lỗi hoặc trả về sai định dạng", HttpStatus.BAD_GATEWAY);
    }
  }

  @Override
  public <T> T generateWithImages(
      String systemPrompt, String userPrompt, java.util.List<Image> images, Class<T> outputType) {
    try {
      return chat.prompt()
          .options(
              org.springframework.ai.chat.prompt.ChatOptions.builder().model(visionModel).build())
          .system(systemPrompt)
          .user(
              u -> {
                u.text(userPrompt);
                for (var img : images) {
                  u.media(
                      org.springframework.util.MimeTypeUtils.parseMimeType(img.mimeType()),
                      new org.springframework.core.io.ByteArrayResource(img.data()));
                }
              })
          .call()
          .entity(outputType);
    } catch (ResourceAccessException e) {
      throw new BusinessException(
          "LLM_UNAVAILABLE", "Mô hình vision không khả dụng", HttpStatus.BAD_GATEWAY);
    } catch (RuntimeException e) {
      if (e instanceof BusinessException) {
        throw e;
      }
      throw new BusinessException(
          "LLM_UNAVAILABLE",
          "Mô hình vision lỗi hoặc trả về sai định dạng",
          HttpStatus.BAD_GATEWAY);
    }
  }

  @Override
  public void stream(
      String systemPrompt, String userPrompt, java.util.function.Consumer<String> onToken) {
    try {
      chat.prompt().system(systemPrompt).user(userPrompt).stream()
          .content()
          .toIterable()
          .forEach(onToken);
    } catch (RuntimeException e) {
      if (e instanceof BusinessException) {
        throw e;
      }
      throw new BusinessException("LLM_UNAVAILABLE", "LLM không khả dụng", HttpStatus.BAD_GATEWAY);
    }
  }

  @Override
  public String modelName() {
    return modelName;
  }
}
