package vn.edu.uet.erpai.knowledge.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.uet.erpai.common.security.SecurityUtils;
import vn.edu.uet.erpai.common.web.PageResponse;
import vn.edu.uet.erpai.knowledge.domain.KnowledgeDocument.DocType;
import vn.edu.uet.erpai.knowledge.domain.KnowledgeDocument.Status;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievalResult;
import vn.edu.uet.erpai.knowledge.service.DocumentResponse;
import vn.edu.uet.erpai.knowledge.service.KnowledgeSearchService;
import vn.edu.uet.erpai.knowledge.service.KnowledgeService;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

  public record SearchRequest(
      @NotBlank String query,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
      Integer topK) {}

  private final KnowledgeService service;
  private final KnowledgeSearchService search;

  public KnowledgeController(KnowledgeService service, KnowledgeSearchService search) {
    this.service = service;
    this.search = search;
  }

  @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DocumentResponse upload(
      @RequestPart("file") MultipartFile file,
      @RequestParam @NotBlank String title,
      @RequestParam(required = false) String docNumber,
      @RequestParam DocType docType,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate issuedDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate effectiveFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate effectiveTo,
      @RequestParam(required = false) String sourceUrl)
      throws IOException {
    var req =
        new KnowledgeService.CreateRequest(
            title, docNumber, docType, issuedDate, effectiveFrom, effectiveTo, sourceUrl);
    return service.create(
        SecurityUtils.currentUser(),
        req,
        file.getBytes(),
        file.getOriginalFilename(),
        file.getContentType());
  }

  @GetMapping("/documents")
  public PageResponse<DocumentResponse> list(
      @RequestParam(required = false) Status status,
      @RequestParam(required = false) DocType docType,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var result =
        service.list(
            status,
            docType,
            q,
            PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")));
    return new PageResponse<>(
        result.getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @GetMapping("/documents/{id}")
  public DocumentResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping("/documents/{id}/chunks")
  public List<KnowledgeService.ChunkResponse> chunks(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.chunks(id, page, size);
  }

  /** Sửa metadata (title, docNumber, docType, ngày, nguồn); trường không gửi giữ nguyên. */
  @PatchMapping("/documents/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public DocumentResponse update(
      @PathVariable UUID id, @RequestBody KnowledgeService.CreateRequest req) {
    return service.update(SecurityUtils.currentUser(), id, req);
  }

  @DeleteMapping("/documents/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void archive(@PathVariable UUID id) {
    service.archive(SecurityUtils.currentUser(), id);
  }

  @PostMapping("/documents/{id}/reindex")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DocumentResponse reindex(@PathVariable UUID id) {
    return service.reindex(SecurityUtils.currentUser(), id);
  }

  /** Tìm kiếm tri thức (hybrid + rerank). Dùng cho giao diện kiểm tra và debug truy xuất. */
  @PostMapping("/search")
  public RetrievalResult search(@Valid @RequestBody SearchRequest req) {
    return search.search(req.query(), req.asOf(), req.topK());
  }
}
