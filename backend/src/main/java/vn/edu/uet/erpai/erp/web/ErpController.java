package vn.edu.uet.erpai.erp.web;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.erpai.common.security.SecurityUtils;
import vn.edu.uet.erpai.common.web.PageResponse;
import vn.edu.uet.erpai.erp.model.ErpAccount;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.service.ErpQueryService;

@RestController
@RequestMapping("/api/v1/erp")
public class ErpController {

  private final ErpQueryService service;

  public ErpController(ErpQueryService service) {
    this.service = service;
  }

  /** Hóa đơn mua hàng, hóa đơn bán hàng và phiếu chi (tham số entityType). */
  @GetMapping("/vouchers")
  public PageResponse<ErpVoucherResponse> vouchers(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo,
      @RequestParam(defaultValue = "PURCHASE_INVOICE") ErpEntityType entityType,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var companyId = SecurityUtils.currentUser().companyId();
    var result = service.listVouchers(entityType, companyId, periodFrom, periodTo, page, size);
    int pages = (int) Math.ceil(result.totalItems() / (double) result.size());
    return new PageResponse<>(
        result.items().stream().map(ErpVoucherResponse::from).toList(),
        result.page(),
        result.size(),
        result.totalItems(),
        pages);
  }

  @GetMapping("/vouchers/{entityRef}")
  public ErpVoucherResponse voucher(
      @PathVariable String entityRef,
      @RequestParam(defaultValue = "PURCHASE_INVOICE") ErpEntityType entityType) {
    return ErpVoucherResponse.from(
        service.getVoucher(entityType, SecurityUtils.currentUser().companyId(), entityRef));
  }

  @GetMapping("/accounts")
  public List<ErpAccount> accounts(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
    return service.listAccounts(SecurityUtils.currentUser().companyId(), page, size);
  }
}
