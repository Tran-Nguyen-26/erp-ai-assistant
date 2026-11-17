package vn.edu.uet.erpai.agent.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import vn.edu.uet.erpai.common.exception.BusinessException;

@Component
public class SpringAiLlmClient implements LlmClient {

  private final ChatClient chat;
  private final String modelName;

  public SpringAiLlmClient(
      ChatModel chatModel,
      @Value("${spring.ai.ollama.chat.options.model:qwen2.5:7b}") String modelName) {
    this.chat = ChatClient.builder(chatModel).build();
    this.modelName = modelName;
  }

  @Override
  public <T> T generate(String systemPrompt, String userPrompt, Class<T> outputType) {
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
