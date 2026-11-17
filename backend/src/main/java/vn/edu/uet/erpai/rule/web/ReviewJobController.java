package vn.edu.uet.erpai.rule.web;

import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import vn.edu.uet.erpai.rule.review.ReviewJobResponse;
import vn.edu.uet.erpai.rule.review.ReviewJobService;

@RestController
@RequestMapping("/api/v1/review-jobs")
public class ReviewJobController {

  private final ReviewJobService service;

  public ReviewJobController(ReviewJobService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN')")
  public ResponseEntity<ReviewJobResponse> create(@RequestBody ReviewJobService.CreateRequest req) {
    var job = service.create(SecurityUtils.currentUser(), req);
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/review-jobs/" + job.id()))
        .body(job);
  }

  @GetMapping
  public PageResponse<ReviewJobResponse> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var result =
        service.list(
            SecurityUtils.currentUser().companyId(),
            status,
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
  public ReviewJobResponse get(@PathVariable UUID id) {
    return service.get(SecurityUtils.currentUser().companyId(), id);
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN')")
  public ReviewJobResponse cancel(@PathVariable UUID id) {
    return service.cancel(SecurityUtils.currentUser(), id);
  }
}
