package vn.edu.uet.erpai.knowledge.retrieval;

import java.time.LocalDate;
import java.util.UUID;

public record RetrievedChunk(
    UUID chunkId,
    UUID documentId,
    String docNumber,
    String docTitle,
    String headingPath,
    String content,
    LocalDate effectiveFrom,
    double rrfScore,
    Double rerankScore) {

  public RetrievedChunk withRerankScore(double score) {
    return new RetrievedChunk(
        chunkId,
        documentId,
        docNumber,
        docTitle,
        headingPath,
        content,
        effectiveFrom,
        rrfScore,
        score);
  }
}
