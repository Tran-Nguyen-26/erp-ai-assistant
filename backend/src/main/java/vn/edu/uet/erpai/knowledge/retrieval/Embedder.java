package vn.edu.uet.erpai.knowledge.retrieval;

import java.util.List;

/** Cổng sinh embedding (BGE-M3 qua Ollama). Tách khỏi Spring AI để test bằng bản giả. */
public interface Embedder {

  float[] embed(String text);

  List<float[]> embedAll(List<String> texts);

  String modelName();
}
