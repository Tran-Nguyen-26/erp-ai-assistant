package vn.edu.uet.erpai.review.service;

public enum ExportFormat {
  CSV,
  XLSX,
  PDF;

  public static ExportFormat parse(String value) {
    if (value == null || value.isBlank()) {
      return CSV;
    }
    try {
      return valueOf(value.strip().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new vn.edu.uet.erpai.common.exception.BusinessException(
          "PROPOSAL_EXPORT_FORMAT_UNSUPPORTED", "format phải là CSV, XLSX hoặc PDF");
    }
  }
}
