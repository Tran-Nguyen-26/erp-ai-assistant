package vn.edu.uet.erpai.knowledge.service;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.audit.service.ActorType;
import vn.edu.uet.erpai.audit.service.AuditEvent;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.common.storage.FileStorageService;
import vn.edu.uet.erpai.knowledge.domain.KnowledgeDocument;
import vn.edu.uet.erpai.knowledge.domain.KnowledgeDocument.DocType;
import vn.edu.uet.erpai.knowledge.domain.KnowledgeDocument.Status;
import vn.edu.uet.erpai.knowledge.repository.KnowledgeDocumentRepository;

@Service
public class KnowledgeService {

  public record CreateRequest(
      String title,
      String docNumber,
      DocType docType,
      LocalDate issuedDate,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String sourceUrl) {}

  public record ChunkResponse(
      UUID id,
      int chunkIndex,
      String headingPath,
      String articleNo,
      String clauseNo,
      String content) {}

  private static final long MAX_BYTES = 10L * 1024 * 1024;

  private final KnowledgeDocumentRepository documents;
  private final FileStorageService storage;
  private final JdbcTemplate jdbc;
  private final ApplicationEventPublisher events;

  public KnowledgeService(
      KnowledgeDocumentRepository documents,
      FileStorageService storage,
      JdbcTemplate jdbc,
      ApplicationEventPublisher events) {
    this.documents = documents;
    this.storage = storage;
    this.jdbc = jdbc;
    this.events = events;
  }

  @Transactional
  public DocumentResponse create(
      CurrentUser user, CreateRequest req, byte[] content, String fileName, String contentType) {
    if (content == null || content.length == 0) {
      throw new BusinessException("KNOWLEDGE_EMPTY_FILE", "File rỗng");
    }
    if (content.length > MAX_BYTES) {
      throw new BusinessException(
          "KNOWLEDGE_FILE_TOO_LARGE", "File vượt quá 10 MB", HttpStatus.PAYLOAD_TOO_LARGE);
    }
    if (req.effectiveFrom() != null
        && req.effectiveTo() != null
        && !req.effectiveTo().isAfter(req.effectiveFrom())) {
      throw new BusinessException(
          "KNOWLEDGE_INVALID_EFFECTIVE_RANGE", "effectiveTo phải sau effectiveFrom");
    }
    var stored = storage.save(content, fileName, contentType, "KNOWLEDGE", user.id());
    var doc =
        documents.save(
            new KnowledgeDocument(
                req.title(),
                req.docNumber(),
                req.docType(),
                req.issuedDate(),
                req.effectiveFrom(),
                req.effectiveTo(),
                req.sourceUrl(),
                stored.id()));
    audit(user, "KNOWLEDGE_DOCUMENT_UPLOADED", doc.getId());
    events.publishEvent(new IngestRequested(doc.getId()));
    return DocumentResponse.from(doc);
  }

  @Transactional(readOnly = true)
  public Page<DocumentResponse> list(Status status, DocType type, String q, Pageable pageable) {
    Specification<KnowledgeDocument> spec =
        (root, query, cb) -> {
          var p = new ArrayList<Predicate>();
          if (status != null) p.add(cb.equal(root.get("status"), status));
          if (type != null) p.add(cb.equal(root.get("docType"), type));
          if (q != null && !q.isBlank()) {
            var like = "%" + q.toLowerCase() + "%";
            p.add(
                cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("docNumber"), "")), like)));
          }
          return cb.and(p.toArray(Predicate[]::new));
        };
    return documents.findAll(spec, pageable).map(DocumentResponse::from);
  }

  @Transactional(readOnly = true)
  public DocumentResponse get(UUID id) {
    return DocumentResponse.from(find(id));
  }

  @Transactional(readOnly = true)
  public List<ChunkResponse> chunks(UUID id, int page, int size) {
    find(id);
    return jdbc.query(
        "SELECT id, chunk_index, heading_path, article_no, clause_no, content FROM knowledge_chunk"
            + " WHERE document_id = ? ORDER BY chunk_index LIMIT ? OFFSET ?",
        (rs, i) ->
            new ChunkResponse(
                rs.getObject("id", UUID.class),
                rs.getInt("chunk_index"),
                rs.getString("heading_path"),
                rs.getString("article_no"),
                rs.getString("clause_no"),
                rs.getString("content")),
        id,
        Math.min(Math.max(size, 1), 100),
        (long) Math.max(page, 0) * Math.min(Math.max(size, 1), 100));
  }

  /** Sửa metadata (null = giữ nguyên). Đổi ngày hiệu lực có tác dụng ngay với truy xuất. */
  @Transactional
  public DocumentResponse update(CurrentUser user, UUID id, CreateRequest req) {
    var d = find(id);
    var from = req.effectiveFrom() != null ? req.effectiveFrom() : d.getEffectiveFrom();
    var to = req.effectiveTo() != null ? req.effectiveTo() : d.getEffectiveTo();
    if (from != null && to != null && !to.isAfter(from)) {
      throw new BusinessException(
          "KNOWLEDGE_INVALID_EFFECTIVE_RANGE", "effectiveTo phải sau effectiveFrom");
    }
    d.updateMetadata(
        req.title() != null && !req.title().isBlank() ? req.title() : d.getTitle(),
        req.docNumber() != null ? req.docNumber() : d.getDocNumber(),
        req.docType() != null ? req.docType() : d.getDocType(),
        req.issuedDate() != null ? req.issuedDate() : d.getIssuedDate(),
        from,
        to,
        req.sourceUrl() != null ? req.sourceUrl() : d.getSourceUrl());
    audit(user, "KNOWLEDGE_DOCUMENT_UPDATED", id);
    return DocumentResponse.from(d);
  }

  /** Lưu trữ: chuyển ARCHIVED và xóa chunk (tài liệu không còn được truy xuất). */
  @Transactional
  public void archive(CurrentUser user, UUID id) {
    var d = find(id);
    if (d.getStatus() == Status.INGESTING) {
      throw new BusinessException(
          "KNOWLEDGE_ALREADY_INGESTING", "Tài liệu đang được nạp", HttpStatus.CONFLICT);
    }
    jdbc.update("DELETE FROM knowledge_chunk WHERE document_id = ?", id);
    d.archive();
    audit(user, "KNOWLEDGE_DOCUMENT_ARCHIVED", id);
  }

  @Transactional
  public DocumentResponse reindex(CurrentUser user, UUID id) {
    var doc = find(id);
    if (doc.getStatus() == Status.INGESTING) {
      throw new BusinessException(
          "KNOWLEDGE_ALREADY_INGESTING", "Tài liệu đang được nạp", HttpStatus.CONFLICT);
    }
    audit(user, "KNOWLEDGE_DOCUMENT_REINDEXED", id);
    events.publishEvent(new IngestRequested(id));
    return DocumentResponse.from(doc);
  }

  private KnowledgeDocument find(UUID id) {
    return documents
        .findById(id)
        .orElseThrow(
            () ->
                new BusinessException(
                    "KNOWLEDGE_DOCUMENT_NOT_FOUND",
                    "Không tìm thấy tài liệu",
                    HttpStatus.NOT_FOUND));
  }

  private void audit(CurrentUser user, String action, UUID id) {
    events.publishEvent(
        new AuditEvent(
            ActorType.USER,
            user.id().toString(),
            user.companyId(),
            action,
            "knowledge_document",
            id.toString(),
            Map.of()));
  }
}
