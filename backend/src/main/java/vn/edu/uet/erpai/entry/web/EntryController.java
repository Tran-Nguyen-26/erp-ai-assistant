package vn.edu.uet.erpai.entry.web;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.SecurityUtils;
import vn.edu.uet.erpai.common.web.PageResponse;
import vn.edu.uet.erpai.entry.service.EntryDraftResponse;
import vn.edu.uet.erpai.entry.service.EntryDraftService;

@RestController
@RequestMapping("/api/v1/entry/drafts")
public class EntryController {

  private final EntryDraftService service;

  public EntryController(EntryDraftService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<EntryDraftResponse> create(@RequestPart("file") MultipartFile file)
      throws IOException {
    var draft =
        service.createFromXml(
            SecurityUtils.currentUser(),
            file.getBytes(),
            file.getOriginalFilename(),
            file.getContentType());
    return ResponseEntity.created(URI.create("/api/v1/entry/drafts/" + draft.id())).body(draft);
  }

  @GetMapping
  public PageResponse<EntryDraftResponse> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    var result =
        service.list(
            SecurityUtils.currentUser(),
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

  @GetMapping("/{id}")
  public EntryDraftResponse get(@PathVariable UUID id) {
    return service.get(SecurityUtils.currentUser(), id);
  }

  @PutMapping("/{id}")
  public EntryDraftResponse update(
      @PathVariable UUID id, @RequestBody EntryDraftService.UpdateRequest req) {
    return service.update(SecurityUtils.currentUser(), id, req);
  }

  @PostMapping("/{id}/validate")
  public EntryDraftResponse validate(@PathVariable UUID id) {
    return service.validate(SecurityUtils.currentUser(), id);
  }

  @PostMapping("/{id}/confirm")
  public EntryDraftResponse confirm(@PathVariable UUID id) {
    return service.confirm(SecurityUtils.currentUser(), id);
  }

  /** Xuất dữ liệu nháp (JSON) để nhập vào ERP thủ công; hệ thống không ghi ERP. */
  @GetMapping("/{id}/export")
  public EntryDraftResponse export(
      @PathVariable UUID id, @RequestParam(defaultValue = "json") String format) {
    if (!"json".equalsIgnoreCase(format)) {
      throw new BusinessException("ENTRY_EXPORT_FORMAT_UNSUPPORTED", "Hiện chỉ hỗ trợ format=json");
    }
    return service.get(SecurityUtils.currentUser(), id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void discard(@PathVariable UUID id) {
    service.discard(SecurityUtils.currentUser(), id);
  }
}
