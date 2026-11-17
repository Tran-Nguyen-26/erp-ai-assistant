package vn.edu.uet.erpai.knowledge.retrieval;

import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SpringAiEmbedder implements Embedder {

  private final EmbeddingModel model;
  private final String modelName;

  public SpringAiEmbedder(
      EmbeddingModel model,
      @Value("${spring.ai.ollama.embedding.options.model:bge-m3}") String modelName) {
    this.model = model;
    this.modelName = modelName;
  }

  @Override
  public float[] embed(String text) {
    return model.embed(text);
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    return model.embed(texts);
  }

  @Override
  public String modelName() {
    return modelName;
  }
}
