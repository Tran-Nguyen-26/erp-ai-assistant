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
  private final vn.edu.uet.erpai.erp.adapter.ErpSalesPaymentAdapter salesPayment;

  public ErpQueryService(
      ErpVoucherAdapter adapter, vn.edu.uet.erpai.erp.adapter.ErpSalesPaymentAdapter salesPayment) {
    this.adapter = adapter;
    this.salesPayment = salesPayment;
  }

  /** Loại chứng từ ERP đã có adapter (rà soát tức thời, job hàng loạt, đề xuất sửa, chat). */
  public static final java.util.Set<vn.edu.uet.erpai.erp.model.ErpEntityType> SUPPORTED_TYPES =
      java.util.EnumSet.of(
          vn.edu.uet.erpai.erp.model.ErpEntityType.PURCHASE_INVOICE,
          vn.edu.uet.erpai.erp.model.ErpEntityType.SALES_INVOICE,
          vn.edu.uet.erpai.erp.model.ErpEntityType.PAYMENT);

  /**
   * Tải chứng từ theo loại. Khóa hóa đơn bán hàng có dạng {@code GUID} hoặc {@code GUID@yyyy-MM-dd}
   * (kèm ngày để tra cứu nhanh); các loại khác chỉ là GUID.
   */
  public ErpVoucher getVoucher(
      vn.edu.uet.erpai.erp.model.ErpEntityType type, String companyId, String entityRef) {
    return switch (type) {
      case PURCHASE_INVOICE -> getPurchaseInvoice(companyId, entityRef);
      case SALES_INVOICE -> getSalesInvoice(companyId, entityRef);
      case PAYMENT -> getPayment(companyId, entityRef);
      default ->
          throw new BusinessException(
              "RULE_ENTITY_TYPE_UNSUPPORTED", "Chưa hỗ trợ loại chứng từ " + type);
    };
  }

  public ErpVoucher getSalesInvoice(String companyId, String entityRef) {
    var company = guid(companyId, "companyId");
    var parts = entityRef == null ? new String[] {null} : entityRef.split("@", 2);
    var id = guid(parts[0], "entityRef");
    java.util.Optional<LocalDate> date = java.util.Optional.empty();
    if (parts.length == 2) {
      try {
        date = java.util.Optional.of(LocalDate.parse(parts[1]));
      } catch (java.time.format.DateTimeParseException e) {
        throw new BusinessException("ERP_INVALID_REF", "entityRef: ngày phải dạng yyyy-MM-dd");
      }
    }
    var d = date;
    return guard(() -> salesPayment.findSalesInvoice(company, id, d))
        .orElseThrow(
            () ->
                new BusinessException(
                    "ERP_VOUCHER_NOT_FOUND", "Không tìm thấy chứng từ", HttpStatus.NOT_FOUND));
  }

  public ErpVoucher getPayment(String companyId, String entityRef) {
    var company = guid(companyId, "companyId");
    var id = guid(entityRef, "entityRef");
    return guard(() -> salesPayment.findPayment(company, id))
        .orElseThrow(
            () ->
                new BusinessException(
                    "ERP_VOUCHER_NOT_FOUND", "Không tìm thấy chứng từ", HttpStatus.NOT_FOUND));
  }

  public ErpPage<ErpVoucher> listVouchers(
      vn.edu.uet.erpai.erp.model.ErpEntityType type,
      String companyId,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    return switch (type) {
      case PURCHASE_INVOICE -> listPurchaseInvoices(companyId, from, to, page, size);
      case SALES_INVOICE -> {
        validatePeriod(from, to);
        var company = guid(companyId, "companyId");
        yield guard(
            () ->
                salesPayment.listSalesInvoices(company, from, to, safePage(page), safeSize(size)));
      }
      case PAYMENT -> {
        validatePeriod(from, to);
        var company = guid(companyId, "companyId");
        yield guard(
            () -> salesPayment.listPayments(company, from, to, safePage(page), safeSize(size)));
      }
      default ->
          throw new BusinessException(
              "RULE_ENTITY_TYPE_UNSUPPORTED", "Chưa hỗ trợ loại chứng từ " + type);
    };
  }

  public long countVouchers(
      vn.edu.uet.erpai.erp.model.ErpEntityType type,
      String companyId,
      LocalDate from,
      LocalDate to) {
    return switch (type) {
      case PURCHASE_INVOICE -> countPurchaseInvoices(companyId, from, to);
      case SALES_INVOICE -> {
        validatePeriod(from, to);
        var company = guid(companyId, "companyId");
        yield guard(() -> salesPayment.countSalesInvoices(company, from, to));
      }
      case PAYMENT -> {
        validatePeriod(from, to);
        var company = guid(companyId, "companyId");
        yield guard(() -> salesPayment.countPayments(company, from, to));
      }
      default ->
          throw new BusinessException(
              "RULE_ENTITY_TYPE_UNSUPPORTED", "Chưa hỗ trợ loại chứng từ " + type);
    };
  }

  /** Khóa chứng từ trong kỳ (để chia batch rà soát); phân trang bắt buộc. */
  public List<String> listVoucherIds(
      vn.edu.uet.erpai.erp.model.ErpEntityType type,
      String companyId,
      LocalDate from,
      LocalDate to,
      long offset,
      int size) {
    return switch (type) {
      case PURCHASE_INVOICE -> listPurchaseInvoiceIds(companyId, from, to, offset, size);
      case SALES_INVOICE -> {
        validatePeriod(from, to);
        var company = guid(companyId, "companyId");
        int safe = Math.min(Math.max(size, 1), MAX_ID_PAGE_SIZE);
        yield guard(
            () -> salesPayment.listSalesInvoiceIds(company, from, to, Math.max(offset, 0), safe));
      }
      case PAYMENT -> {
        validatePeriod(from, to);
        var company = guid(companyId, "companyId");
        int safe = Math.min(Math.max(size, 1), MAX_ID_PAGE_SIZE);
        yield guard(
            () -> salesPayment.listPaymentIds(company, from, to, Math.max(offset, 0), safe));
      }
      default ->
          throw new BusinessException(
              "RULE_ENTITY_TYPE_UNSUPPORTED", "Chưa hỗ trợ loại chứng từ " + type);
    };
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
    for (var key : List.of("materialGoodsId", "partnerId")) {
      checked.computeIfPresent(key, (k, v) -> guid(String.valueOf(v), key));
    }
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
