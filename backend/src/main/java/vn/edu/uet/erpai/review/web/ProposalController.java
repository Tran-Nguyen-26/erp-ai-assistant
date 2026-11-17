package vn.edu.uet.erpai.review.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.erpai.common.security.SecurityUtils;
import vn.edu.uet.erpai.common.web.PageResponse;
import vn.edu.uet.erpai.review.domain.Proposal.Status;
import vn.edu.uet.erpai.review.service.CreatorType;
import vn.edu.uet.erpai.review.service.ExportFormat;
import vn.edu.uet.erpai.review.service.ProposalResponse;
import vn.edu.uet.erpai.review.service.ProposalService;
import vn.edu.uet.erpai.review.service.ProposalService.ChangeRequest;

@RestController
@RequestMapping("/api/v1")
public class ProposalController {

  public record CreateProposalRequest(
      String actionType, @NotEmpty List<ChangeRequest> changes, @NotBlank String rationale) {}

  public record ReviewRequest(String note) {}

  public record ExportRequest(@NotEmpty List<UUID> proposalIds, String format) {}

  private final ProposalService service;

  public ProposalController(ProposalService service) {
    this.service = service;
  }

  @GetMapping("/proposals")
  public PageResponse<ProposalResponse> list(
      @RequestParam(required = false) Status status,
      @RequestParam(required = false) String entityRef,
      @RequestParam(required = false) UUID violationId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var result =
        service.list(
            SecurityUtils.currentUser().companyId(),
            status,
            entityRef,
            violationId,
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

  @GetMapping("/proposals/{id}")
  public ProposalResponse get(@PathVariable UUID id) {
    return service.get(SecurityUtils.currentUser().companyId(), id);
  }

  @PostMapping("/violations/{violationId}/proposals")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN')")
  public ResponseEntity<ProposalResponse> create(
      @PathVariable UUID violationId,
      @jakarta.validation.Valid @RequestBody CreateProposalRequest req) {
    if (req.actionType() != null && !"UPDATE_FIELD".equals(req.actionType())) {
      throw new vn.edu.uet.erpai.common.exception.BusinessException(
          "PROPOSAL_ACTION_UNSUPPORTED", "Hiện chỉ hỗ trợ actionType UPDATE_FIELD");
    }
    var created =
        service.create(
            SecurityUtils.currentUser(),
            violationId,
            CreatorType.USER,
            req.changes(),
            req.rationale(),
            null);
    return ResponseEntity.created(URI.create("/api/v1/proposals/" + created.id())).body(created);
  }

  @PostMapping("/proposals/{id}/approve")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN')")
  public ProposalResponse approve(
      @PathVariable UUID id, @RequestBody(required = false) ReviewRequest req) {
    return service.approve(SecurityUtils.currentUser(), id, req == null ? null : req.note());
  }

  @PostMapping("/proposals/{id}/reject")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN')")
  public ProposalResponse reject(
      @PathVariable UUID id, @RequestBody(required = false) ReviewRequest req) {
    return service.reject(SecurityUtils.currentUser(), id, req == null ? null : req.note());
  }

  /** Xuất phiếu đề nghị điều chỉnh: CSV (mặc định), XLSX hoặc PDF. */
  @PostMapping("/proposals/export")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN')")
  public ResponseEntity<byte[]> export(@jakarta.validation.Valid @RequestBody ExportRequest req) {
    var format = ExportFormat.parse(req.format());
    var file = service.export(SecurityUtils.currentUser(), req.proposalIds(), format);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .body(file.content());
  }
}
