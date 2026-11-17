package vn.edu.uet.erpai.knowledge.retrieval;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.knowledge.service.RagProperties;

/**
 * Hybrid search: vector (pgvector, cosine) + full-text, hợp nhất bằng Reciprocal Rank Fusion
 * (k=60).
 */
@Component
public class HybridRetriever {

  private static final String SQL =
      """
      WITH params AS (
          SELECT ?::vector AS qv, plainto_tsquery('simple', immutable_unaccent(?)) AS qt
      ),
      valid_docs AS (
          SELECT id FROM knowledge_document
          WHERE status = 'READY'
            AND (effective_from IS NULL OR effective_from <= ?)
            AND (effective_to   IS NULL OR effective_to   >  ?)
      ),
      vec AS (
          SELECT c.id, ROW_NUMBER() OVER (ORDER BY c.embedding <=> p.qv) AS rnk
          FROM knowledge_chunk c, params p
          WHERE c.document_id IN (SELECT id FROM valid_docs)
          ORDER BY c.embedding <=> p.qv
          LIMIT %1$d
      ),
      fts AS (
          SELECT c.id, ROW_NUMBER() OVER (ORDER BY ts_rank_cd(c.content_tsv, p.qt) DESC) AS rnk
          FROM knowledge_chunk c, params p
          WHERE c.document_id IN (SELECT id FROM valid_docs)
            AND c.content_tsv @@ p.qt
          ORDER BY ts_rank_cd(c.content_tsv, p.qt) DESC
          LIMIT %1$d
      ),
      fused AS (
          SELECT id, SUM(1.0 / (60 + rnk)) AS rrf
          FROM (SELECT * FROM vec UNION ALL SELECT * FROM fts) u
          GROUP BY id
      )
      SELECT c.id, c.document_id, d.doc_number, d.title, d.effective_from,
             c.heading_path, c.content, f.rrf
      FROM fused f
      JOIN knowledge_chunk c ON c.id = f.id
      JOIN knowledge_document d ON d.id = c.document_id
      ORDER BY f.rrf DESC
      LIMIT %1$d
      """;

  private final JdbcTemplate jdbc;
  private final Embedder embedder;
  private final RagProperties props;

  public HybridRetriever(JdbcTemplate jdbc, Embedder embedder, RagProperties props) {
    this.jdbc = jdbc;
    this.embedder = embedder;
    this.props = props;
  }

  /**
   * @param asOf ngày chứng từ (hoặc ngày hiện tại) — chỉ lấy tài liệu còn hiệu lực tại ngày này
   */
  public List<RetrievedChunk> retrieve(String query, LocalDate asOf) {
    var vector = toVectorLiteral(embedder.embed(query));
    var date = Date.valueOf(asOf);
    return jdbc.query(
        SQL.formatted(props.candidates()),
        (rs, i) ->
            new RetrievedChunk(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("doc_number"),
                rs.getString("title"),
                rs.getString("heading_path"),
                rs.getString("content"),
                rs.getDate("effective_from") == null
                    ? null
                    : rs.getDate("effective_from").toLocalDate(),
                rs.getDouble("rrf"),
                null),
        vector,
        query,
        date,
        date);
  }

  public static String toVectorLiteral(float[] v) {
    var sb = new StringBuilder("[");
    for (int i = 0; i < v.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(v[i]);
    }
    return sb.append(']').toString();
  }
}
