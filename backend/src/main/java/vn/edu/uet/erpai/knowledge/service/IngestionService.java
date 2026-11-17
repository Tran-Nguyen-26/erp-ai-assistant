package vn.edu.uet.erpai.knowledge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.uet.erpai.common.storage.FileStorageService;
import vn.edu.uet.erpai.knowledge.domain.KnowledgeDocument;
import vn.edu.uet.erpai.knowledge.domain.KnowledgeDocument.DocType;
import vn.edu.uet.erpai.knowledge.domain.KnowledgeDocument.Status;
import vn.edu.uet.erpai.knowledge.ingest.Chunk;
import vn.edu.uet.erpai.knowledge.ingest.DocumentParser;
import vn.edu.uet.erpai.knowledge.ingest.LegalChunker;
import vn.edu.uet.erpai.knowledge.repository.KnowledgeDocumentRepository;
import vn.edu.uet.erpai.knowledge.retrieval.Embedder;
import vn.edu.uet.erpai.knowledge.retrieval.HybridRetriever;

/** Parse → chunk → embedding → lưu knowledge_chunk. Embedding chạy ngoài transaction (chậm). */
@Service
public class IngestionService {

  private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
  private static final int MANUAL_OVERLAP_TOKENS = 64;

  private final KnowledgeDocumentRepository documents;
  private final FileStorageService storage;
  private final DocumentParser parser;
  private final Embedder embedder;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final RagProperties props;

  public IngestionService(
      KnowledgeDocumentRepository documents,
      FileStorageService storage,
      DocumentParser parser,
      Embedder embedder,
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      RagProperties props) {
    this.documents = documents;
    this.storage = storage;
    this.parser = parser;
    this.embedder = embedder;
    this.jdbc = jdbc;
    this.tx = tx;
    this.props = props;
  }

  /**
   * Nạp (hoặc nạp lại) một tài liệu. Lỗi được ghi vào document (status FAILED), không ném ra ngoài.
   */
  public void ingest(UUID documentId) {
    var doc =
        tx.execute(
            s -> {
              var d = documents.findById(documentId).orElseThrow();
              d.markIngesting();
              return documents.save(d);
            });
    try {
      var text = parser.parse(storage.read(doc.getFileId()), storage.originalName(doc.getFileId()));
      int overlap =
          doc.getDocType() == DocType.MANUAL || doc.getDocType() == DocType.INTERNAL
              ? MANUAL_OVERLAP_TOKENS
              : 0;
      List<Chunk> chunks = new LegalChunker(props.maxChunkTokens(), overlap).chunk(text);
      if (chunks.isEmpty()) {
        throw new IllegalStateException("Không trích được nội dung (PDF scan cần OCR?)");
      }
      var vectors = embed(doc, chunks);
      tx.executeWithoutResult(
          s -> {
            jdbc.update("DELETE FROM knowledge_chunk WHERE document_id = ?", documentId);
            jdbc.batchUpdate(
                "INSERT INTO knowledge_chunk (document_id, chunk_index, heading_path, article_no,"
                    + " clause_no, content, token_count, embedding) VALUES (?,?,?,?,?,?,?,?::vector)",
                chunks,
                200,
                (ps, c) -> {
                  ps.setObject(1, documentId);
                  ps.setInt(2, c.index());
                  ps.setString(3, c.headingPath());
                  ps.setString(4, c.articleNo());
                  ps.setString(5, c.clauseNo());
                  ps.setString(6, c.content());
                  ps.setInt(7, c.tokenCount());
                  ps.setString(8, HybridRetriever.toVectorLiteral(vectors.get(c.index())));
                });
            var d = documents.findById(documentId).orElseThrow();
            d.markReady(chunks.size(), embedder.modelName());
          });
      log.info("Đã nạp tài liệu {} ({} chunk)", documentId, chunks.size());
    } catch (RuntimeException e) {
      log.warn("Nạp tài liệu {} thất bại: {}", documentId, e.getMessage());
      tx.executeWithoutResult(
          s -> documents.findById(documentId).orElseThrow().markFailed(e.getMessage()));
    }
  }

  private List<float[]> embed(KnowledgeDocument doc, List<Chunk> chunks) {
    var prefix = doc.getDocNumber() == null ? doc.getTitle() : doc.getDocNumber();
    var out = new ArrayList<float[]>(chunks.size());
    int batch = Math.max(1, props.embeddingBatchSize());
    for (int i = 0; i < chunks.size(); i += batch) {
      // headingPath được ghép vào nội dung trước khi embed để giữ ngữ cảnh pháp lý
      var texts =
          chunks.subList(i, Math.min(chunks.size(), i + batch)).stream()
              .map(c -> "[" + prefix + "] " + c.headingPath() + ": " + c.content())
              .toList();
      var vectors = embedder.embedAll(texts);
      for (var v : vectors) {
        if (v.length != props.embeddingDimensions()) {
          throw new IllegalStateException(
              "Embedding có "
                  + v.length
                  + " chiều, cần "
                  + props.embeddingDimensions()
                  + " (kiểm tra AI_EMBEDDING_DIMENSIONS / model)");
        }
        out.add(v);
      }
    }
    return out;
  }

  public Status statusOf(UUID id) {
    return documents.findById(id).orElseThrow().getStatus();
  }
}
