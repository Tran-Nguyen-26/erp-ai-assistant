package vn.edu.uet.erpai.erp.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.erp.adapter.ErpVoucherAdapter;
import vn.edu.uet.erpai.erp.model.ErpAccount;
import vn.edu.uet.erpai.erp.model.ErpPage;
import vn.edu.uet.erpai.erp.model.ErpVoucher;

/**
 * API công khai của module ERP (chỉ đọc). Mọi truy vấn bắt buộc có companyId. Lỗi hạ tầng của DB
 * ERP được dịch thành {@code ERP_UNAVAILABLE} (502) / {@code ERP_QUERY_TIMEOUT} (504).
 */
@Service
@Transactional(readOnly = true, transactionManager = "erpTransactionManager")
public class ErpQueryService {

  public static final int MAX_PAGE_SIZE = 100;
  public static final int MAX_ID_PAGE_SIZE = 5000;
  public static final int MAX_PERIOD_MONTHS = 12;

  private final ErpVoucherAdapter adapter;

  public ErpQueryService(ErpVoucherAdapter adapter) {
    this.adapter = adapter;
  }

  public ErpVoucher getPurchaseInvoice(String companyId, String entityRef) {
    var company = guid(companyId, "companyId");
    var ref = guid(entityRef, "entityRef");
    return guard(() -> adapter.findPurchaseInvoice(company, ref))
        .orElseThrow(
            () ->
                new BusinessException(
                    "ERP_VOUCHER_NOT_FOUND", "Không tìm thấy chứng từ", HttpStatus.NOT_FOUND));
  }

  public ErpPage<ErpVoucher> listPurchaseInvoices(
      String companyId, LocalDate periodFrom, LocalDate periodTo, int page, int size) {
    validatePeriod(periodFrom, periodTo);
    var company = guid(companyId, "companyId");
    return guard(
        () ->
            adapter.listPurchaseInvoices(
                company, periodFrom, periodTo, safePage(page), safeSize(size)));
  }

  public long countPurchaseInvoices(String companyId, LocalDate periodFrom, LocalDate periodTo) {
    validatePeriod(periodFrom, periodTo);
    var company = guid(companyId, "companyId");
    return guard(() -> adapter.countPurchaseInvoices(company, periodFrom, periodTo));
  }

  /** Chỉ lấy khóa chứng từ (để chia batch rà soát); phân trang bắt buộc. */
  public List<String> listPurchaseInvoiceIds(
      String companyId, LocalDate periodFrom, LocalDate periodTo, long offset, int size) {
    validatePeriod(periodFrom, periodTo);
    var company = guid(companyId, "companyId");
    int safe = Math.min(Math.max(size, 1), MAX_ID_PAGE_SIZE);
    return guard(
        () ->
            adapter.listPurchaseInvoiceIds(
                company, periodFrom, periodTo, Math.max(offset, 0), safe));
  }

  /**
   * Truy vấn rule liên chứng từ theo tên file {@code sql/erp/rule-*.sql}. Chỉ tên dạng {@code
   * rule-<kebab>} được phép (chặn đường dẫn tùy ý); mọi tham số truyền dạng tham số có tên.
   */
  public List<java.util.Map<String, Object>> queryRule(
      String sqlRef, java.util.Map<String, Object> params) {
    if (sqlRef == null || !sqlRef.matches("^rule-[a-z0-9-]+$")) {
      throw new BusinessException("ERP_SQL_REF_INVALID", "sqlRef không hợp lệ");
    }
    var checked = new java.util.LinkedHashMap<String, Object>(params);
    checked.computeIfPresent("companyId", (k, v) -> guid(String.valueOf(v), "companyId"));
    checked.computeIfPresent(
        "entityRef", (k, v) -> v == null ? null : guid(String.valueOf(v), "entityRef"));
    return guard(() -> adapter.queryRule(sqlRef, checked));
  }

  /** Đối tượng trong ERP theo MST (dùng gợi ý khi nhập liệu). */
  public java.util.Optional<vn.edu.uet.erpai.erp.model.ErpPartnerRef> findPartnerByTaxCode(
      String companyId, String taxCode) {
    var company = guid(companyId, "companyId");
    if (taxCode == null || taxCode.isBlank()) {
      return java.util.Optional.empty();
    }
    return guard(() -> adapter.findPartnerByTaxCode(company, taxCode.strip()));
  }

  public List<ErpAccount> listAccounts(String companyId, int page, int size) {
    var company = guid(companyId, "companyId");
    return guard(() -> adapter.listAccounts(company, safePage(page), safeSize(size)));
  }

  /** Dịch lỗi truy cập DB ERP sang lỗi nghiệp vụ có mã rõ ràng. */
  private static <T> T guard(Supplier<T> call) {
    try {
      return call.get();
    } catch (QueryTimeoutException e) {
      throw new BusinessException(
          "ERP_QUERY_TIMEOUT", "Truy vấn ERP quá thời gian", HttpStatus.GATEWAY_TIMEOUT);
    } catch (DataAccessException | CannotCreateTransactionException e) {
      throw new BusinessException(
          "ERP_UNAVAILABLE", "Không kết nối được CSDL ERP", HttpStatus.BAD_GATEWAY);
    }
  }

  private static void validatePeriod(LocalDate periodFrom, LocalDate periodTo) {
    if (periodFrom == null || periodTo == null || periodTo.isBefore(periodFrom)) {
      throw new BusinessException("ERP_INVALID_PERIOD", "Kỳ không hợp lệ");
    }
    if (ChronoUnit.MONTHS.between(periodFrom, periodTo.plusDays(1)) > MAX_PERIOD_MONTHS) {
      throw new BusinessException(
          "ERP_PERIOD_TOO_LONG", "Kỳ truy vấn không được vượt quá 12 tháng");
    }
  }

  /** Khóa ERP là GUID; chặn giá trị lạ trước khi chạm DB (tránh lỗi chuyển kiểu của SQL Server). */
  static String guid(String value, String field) {
    try {
      return UUID.fromString(value).toString();
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new BusinessException("ERP_INVALID_REF", field + " phải là GUID");
    }
  }

  private static int safePage(int page) {
    return Math.max(page, 0);
  }

  private static int safeSize(int size) {
    return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
  }
}
