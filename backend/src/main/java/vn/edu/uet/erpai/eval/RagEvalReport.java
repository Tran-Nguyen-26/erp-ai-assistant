package vn.edu.uet.erpai.eval;

/** Kết quả đánh giá truy xuất RAG. Hit@k/MRR chỉ tính trên câu hỏi có đáp án trong kho. */
public record RagEvalReport(
    int questions,
    int answerable,
    double hitAt1,
    double hitAt3,
    double hitAt5,
    double mrr,
    int unanswerable,
    double refusalRate,
    long p50Millis,
    long p95Millis) {}
