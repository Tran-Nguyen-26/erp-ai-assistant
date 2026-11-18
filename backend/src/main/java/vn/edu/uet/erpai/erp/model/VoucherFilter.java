package vn.edu.uet.erpai.erp.model;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Bộ lọc tùy chọn cho danh sách chứng từ: {@code q} khớp một phần số chứng từ / diễn giải / tên đối
 * tượng, {@code partnerTaxCode} khớp chính xác MST đối tượng. Truyền xuống SQL dạng tham số có tên;
 * chuỗi rỗng = không lọc (tránh so sánh với NULL).
 */
public record VoucherFilter(String q, String partnerTaxCode) {

  /** Giới hạn độ dài để chuỗi LIKE do người dùng nhập không thành gánh nặng cho SQL Server. */
  static final int MAX_Q_LENGTH = 100;

  static final int MAX_TAX_CODE_LENGTH = 20;

  public static final VoucherFilter NONE = new VoucherFilter("", "");

  public VoucherFilter {
    q = q == null ? "" : q.strip();
    q = q.length() > MAX_Q_LENGTH ? q.substring(0, MAX_Q_LENGTH) : q;
    partnerTaxCode = partnerTaxCode == null ? "" : partnerTaxCode.strip();
    partnerTaxCode =
        partnerTaxCode.length() > MAX_TAX_CODE_LENGTH
            ? partnerTaxCode.substring(0, MAX_TAX_CODE_LENGTH)
            : partnerTaxCode;
  }

  /**
   * Thêm :q, :qLike (đã escape ký tự đại diện của LIKE) và :partnerTaxCode vào tham số truy vấn.
   */
  public MapSqlParameterSource applyTo(MapSqlParameterSource params) {
    return params
        .addValue("q", q)
        .addValue("qLike", "%" + escapeLike(q) + "%")
        .addValue("partnerTaxCode", partnerTaxCode);
  }

  /**
   * Dùng với {@code ESCAPE '\'} trong SQL: escape \ % _ [ để người dùng không chèn ký tự đại diện.
   */
  static String escapeLike(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
  }
}
