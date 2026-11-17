package vn.edu.uet.erpai.knowledge.ingest;

/** Một đoạn tri thức đã tách. {@code headingPath} dạng "Chương II > Điều 5. Tên > Khoản 2". */
public record Chunk(
    int index,
    String headingPath,
    String articleNo,
    String clauseNo,
    String content,
    int tokenCount) {}
