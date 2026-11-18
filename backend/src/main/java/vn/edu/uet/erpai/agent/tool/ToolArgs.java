package vn.edu.uet.erpai.agent.tool;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import vn.edu.uet.erpai.common.exception.BusinessException;

/** Đọc tham số công cụ do LLM sinh ra một cách chặt chẽ. */
final class ToolArgs {

  private ToolArgs() {}

  static String string(Map<String, Object> args, String key, boolean required) {
    var v = args == null ? null : args.get(key);
    var s = v == null ? null : v.toString().strip();
    if ((s == null || s.isEmpty()) && required) {
      throw new BusinessException("TOOL_ARG_INVALID", "Thiếu tham số " + key);
    }
    return s == null || s.isEmpty() ? null : s;
  }

  static LocalDate date(Map<String, Object> args, String key, boolean required) {
    var s = string(args, key, required);
    try {
      return s == null ? null : LocalDate.parse(s);
    } catch (DateTimeParseException e) {
      throw new BusinessException("TOOL_ARG_INVALID", key + " phải dạng yyyy-MM-dd");
    }
  }

  /** Loại chứng từ ERP (mặc định hóa đơn mua hàng); chỉ nhận loại đã có adapter. */
  static vn.edu.uet.erpai.erp.model.ErpEntityType entityType(Map<String, Object> args) {
    var s = string(args, "entityType", false);
    if (s == null) {
      return vn.edu.uet.erpai.erp.model.ErpEntityType.PURCHASE_INVOICE;
    }
    try {
      var type = vn.edu.uet.erpai.erp.model.ErpEntityType.valueOf(s.toUpperCase());
      if (vn.edu.uet.erpai.erp.service.ErpQueryService.SUPPORTED_TYPES.contains(type)) {
        return type;
      }
    } catch (IllegalArgumentException e) {
      // rơi xuống lỗi bên dưới
    }
    throw new BusinessException(
        "TOOL_ARG_INVALID", "entityType phải là PURCHASE_INVOICE, SALES_INVOICE hoặc PAYMENT");
  }

  /** Số nguyên trong [1, max]; mặc định {@code def}. */
  static int limit(Map<String, Object> args, String key, int def, int max) {
    var v = args == null ? null : args.get(key);
    if (v == null) {
      return def;
    }
    try {
      int n = v instanceof Number num ? num.intValue() : Integer.parseInt(v.toString().strip());
      return Math.min(Math.max(n, 1), max);
    } catch (NumberFormatException e) {
      throw new BusinessException("TOOL_ARG_INVALID", key + " phải là số nguyên");
    }
  }
}
