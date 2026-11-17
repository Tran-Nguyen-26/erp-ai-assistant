package vn.edu.uet.erpai.rule.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Chuẩn hóa evidence trước khi lưu JSONB / trả API: tiền là string decimal, ngày là ISO. */
final class JsonSafe {

  private JsonSafe() {}

  static Map<String, Object> evidence(Map<String, Object> in) {
    var out = new LinkedHashMap<String, Object>();
    in.forEach((k, v) -> out.put(k, convert(v)));
    return out;
  }

  static Object convert(Object v) {
    if (v == null || v instanceof Number && !(v instanceof BigDecimal) || v instanceof Boolean) {
      return v;
    }
    if (v instanceof BigDecimal b) {
      return (b.scale() < 2 ? b.setScale(2, RoundingMode.HALF_UP) : b).toPlainString();
    }
    return v.toString();
  }
}
