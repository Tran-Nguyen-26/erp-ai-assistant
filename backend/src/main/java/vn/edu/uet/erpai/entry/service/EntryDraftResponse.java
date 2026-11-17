package vn.edu.uet.erpai.entry.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.edu.uet.erpai.entry.extractor.ExtractedInvoice;

public record EntryDraftResponse(
    UUID id,
    String sourceType,
    String status,
    ExtractedInvoice invoice,
    PartnerMatch partnerMatch,
    Map<String, Object> fieldConfidence,
    List<String> lowConfidenceFields,
    List<SuggestedEntry> suggestedEntries,
    List<ValidationItem> validation,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  /** Kết quả đối chiếu NCC trong ERP; {@code found = null} nghĩa là chưa kiểm tra được. */
  public record PartnerMatch(Boolean found, String code, String name) {}

  /** {@code amount} là chuỗi decimal. */
  public record SuggestedEntry(
      String debitAccount, String creditAccount, String amount, String reason, double confidence) {}

  public record ValidationItem(String ruleId, String severity, String message) {}
}
