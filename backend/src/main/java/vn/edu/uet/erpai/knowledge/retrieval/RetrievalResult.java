package vn.edu.uet.erpai.knowledge.retrieval;

import java.util.List;

/**
 * Kết quả truy xuất. {@code sufficient = false} nghĩa là điểm rerank cao nhất dưới ngưỡng {@code
 * app.rag.min-score}: người gọi PHẢI trả lời "không đủ căn cứ" thay vì tự suy diễn.
 */
public record RetrievalResult(List<RetrievedChunk> chunks, double maxScore, boolean sufficient) {}
