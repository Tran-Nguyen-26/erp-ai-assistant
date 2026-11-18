package vn.edu.uet.erpai.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.SecurityUtils;
import vn.edu.uet.erpai.common.web.PageResponse;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.model.VoucherFilter;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.rule.service.ViolationService;

/**
 * Tìm chứng từ ERP kèm số vi phạm đang mở (docs/api-spec.md §3). Nằm ở gói {@code web} (tầng tổng
 * hợp) vì cần dữ liệu của cả module {@code erp} lẫn {@code rule}, mà {@code erp} không được phụ
 * thuộc {@code rule}.
 */
@RestController
@RequestMapping("/api/v1/erp")
public class VoucherSearchController {

  public record Partner(String code, String name, String taxCode) {}

  public record VoucherListItem(
      String entityRef,
      String entityType,
      String voucherNo,
      LocalDate voucherDate,
      String description,
      Partner partner,
      String totalAmount,
      String currency,
      int openViolationCount,
      String maxSeverity) {}

  private final ErpQueryService erp;
  private final ViolationService violations;

  public VoucherSearchController(ErpQueryService erp, ViolationService violations) {
    this.erp = erp;
    this.violations = violations;
  }

  /**
   * @param hasViolations true → chỉ chứng từ đang có vi phạm mở (lấy khóa từ bảng violation rồi tải
   *     từng chứng từ từ ERP; q/partnerTaxCode được lọc trên tập kết quả của trang)
   */
  @GetMapping("/vouchers")
  public PageResponse<VoucherListItem> search(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo,
      @RequestParam(required = false) ErpEntityType voucherType,
      @RequestParam(required = false) ErpEntityType entityType,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String partnerTaxCode,
      @RequestParam(defaultValue = "false") boolean hasViolations,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var companyId = SecurityUtils.currentUser().companyId();
    var type =
        voucherType != null
            ? voucherType
            : entityType != null ? entityType : ErpEntityType.PURCHASE_INVOICE;
    if (!ErpQueryService.SUPPORTED_TYPES.contains(type)) {
      throw new BusinessException(
          "RULE_ENTITY_TYPE_UNSUPPORTED", "Chưa hỗ trợ loại chứng từ " + type);
    }
    int safeSize = Math.min(Math.max(size, 1), 100);
    int safePage = Math.max(page, 0);

    List<ErpVoucher> vouchers;
    long total;
    if (hasViolations) {
      var open =
          violations.openEntityRefs(
              companyId, type.name(), periodFrom, periodTo, PageRequest.of(safePage, safeSize));
      vouchers = new ArrayList<>();
      for (var ref : open.refs()) {
        try {
          vouchers.add(erp.getVoucher(type, companyId, ref));
        } catch (BusinessException e) {
          if (!"ERP_VOUCHER_NOT_FOUND".equals(e.getCode())) {
            throw e; // chứng từ đã bị xóa khỏi ERP thì bỏ qua, lỗi khác báo ra
          }
        }
      }
      var filter = new VoucherFilter(q, partnerTaxCode);
      vouchers = vouchers.stream().filter(v -> matches(v, filter)).toList();
      total = open.total();
    } else {
      var result =
          erp.listVouchers(
              type,
              companyId,
              periodFrom,
              periodTo,
              safePage,
              safeSize,
              new VoucherFilter(q, partnerTaxCode));
      vouchers = result.items();
      total = result.totalItems();
    }

    var summary =
        violations.openSummaryByRefs(
            companyId, type.name(), vouchers.stream().map(ErpVoucher::ref).toList());
    var items =
        vouchers.stream()
            .map(
                v -> {
                  var s = summary.get(v.ref());
                  return new VoucherListItem(
                      v.ref(),
                      v.entityType().name(),
                      v.voucherNo(),
                      v.voucherDate(),
                      v.description(),
                      v.partner() == null
                          ? null
                          : new Partner(
                              v.partner().code(), v.partner().name(), v.partner().taxCode()),
                      money(v.totalAmount()),
                      v.currency(),
                      s == null ? 0 : s.openCount(),
                      s == null ? null : s.maxSeverity());
                })
            .toList();
    return new PageResponse<>(
        items, safePage, safeSize, total, (int) Math.ceil(total / (double) safeSize));
  }

  private static boolean matches(ErpVoucher v, VoucherFilter f) {
    if (!f.partnerTaxCode().isEmpty()
        && (v.partner() == null || !f.partnerTaxCode().equals(v.partner().taxCode()))) {
      return false;
    }
    if (f.q().isEmpty()) {
      return true;
    }
    var needle = f.q().toLowerCase(Locale.ROOT);
    return contains(v.voucherNo(), needle)
        || contains(v.description(), needle)
        || (v.partner() != null && contains(v.partner().name(), needle));
  }

  private static boolean contains(String s, String needle) {
    return s != null && s.toLowerCase(Locale.ROOT).contains(needle);
  }

  private static String money(BigDecimal v) {
    return v == null ? null : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }
}
