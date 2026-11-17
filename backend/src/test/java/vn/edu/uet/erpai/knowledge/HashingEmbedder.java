package vn.edu.uet.erpai.knowledge;

import java.text.Normalizer;
import java.util.List;
import vn.edu.uet.erpai.knowledge.retrieval.Embedder;

/** Embedder giả cho test: băm từ vào 1024 chiều (bag-of-words), chuẩn hóa L2. Không cần Ollama. */
public class HashingEmbedder implements Embedder {

  static final int DIM = 1024;

  @Override
  public float[] embed(String text) {
    var v = new float[DIM];
    var folded =
        Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('đ', 'd');
    for (var w : folded.split("[^a-z0-9]+")) {
      if (w.length() > 1) {
        v[Math.floorMod(w.hashCode(), DIM)] += 1f;
      }
    }
    double norm = 0;
    for (var x : v) norm += x * x;
    norm = Math.sqrt(norm);
    if (norm > 0) {
      for (int i = 0; i < DIM; i++) v[i] /= (float) norm;
    }
    return v;
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    return texts.stream().map(this::embed).toList();
  }

  @Override
  public String modelName() {
    return "hashing-test";
  }
}
