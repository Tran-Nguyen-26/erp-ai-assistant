package vn.edu.uet.erpai.entry.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.audit.service.ActorType;
import vn.edu.uet.erpai.audit.service.AuditEvent;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.common.security.Role;
import vn.edu.uet.erpai.common.storage.FileStorageService;
import vn.edu.uet.erpai.entry.domain.EntryDraft;
import vn.edu.uet.erpai.entry.domain.EntryDraft.SourceType;
import vn.edu.uet.erpai.entry.domain.EntryDraft.Status;
import vn.edu.uet.erpai.entry.extractor.EInvoiceXmlExtractor;
import vn.edu.uet.erpai.entry.extractor.ExtractedInvoice;
import vn.edu.uet.erpai.entry.repository.EntryDraftRepository;
import vn.edu.uet.erpai.entry.service.EntryDraftResponse.PartnerMatch;
import vn.edu.uet.erpai.entry.service.EntryDraftResponse.SuggestedEntry;
import vn.edu.uet.erpai.entry.service.EntryDraftResponse.ValidationItem;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpInvoice;
import vn.edu.uet.erpai.erp.model.ErpJournalLine;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.rule.service.RuleCheckService;

/**
 * Hỗ trợ nhập liệu: trích xuất (tất định) → đối chiếu ERP → gợi ý định khoản → kiểm tra bằng rule
 * engine → người dùng sửa/xác nhận. Nháp KHÔNG bao giờ được ghi vào ERP; xác nhận chỉ chốt dữ liệu
 * để xuất/nhập thủ công (ADR 0003).
 */
@Service
public class EntryDraftService {

  private static final long MAX_BYTES = 10L * 1024 * 1024;
  private static final String DEFAULT_DEBIT = "156";
  private static final String DEFAULT_VAT_DEBIT = "1331";
  private static final String DEFAULT_CREDIT = "331";

  public record UpdateRequest(ExtractedInvoice invoice, List<SuggestedEntry> suggestedEntries) {}

  private final EntryDraftRepository drafts;
  private final EInvoiceXmlExtractor extractor;
  private final FileStorageService storage;
  private final ErpQueryService erp;
  private final RuleCheckService rules;
  private final ObjectMapper mapper;
  private final ApplicationEventPublisher events;

  public EntryDraftService(
      EntryDraftRepository drafts,
      EInvoiceXmlExtractor extractor,
      FileStorageService storage,
      ErpQueryService erp,
      RuleCheckService rules,
      ObjectMapper mapper,
      ApplicationEventPublisher events) {
    this.drafts = drafts;
    this.extractor = extractor;
    this.storage = storage;
    this.erp = erp;
    this.rules = rules;
    this.mapper = mapper;
    this.events = events;
  }

  @Transactional
  public EntryDraftResponse createFromXml(
      CurrentUser user, byte[] content, String fileName, String contentType) {
    if (content == null || content.length == 0) {
      throw new BusinessException("ENTRY_EMPTY_FILE", "File rỗng");
    }
    if (content.length > MAX_BYTES) {
      throw new BusinessException(
          "ENTRY_FILE_TOO_LARGE", "File vượt quá 10 MB", HttpStatus.PAYLOAD_TOO_LARGE);
    }
    if (fileName == null || !fileName.toLowerCase().endsWith(".xml")) {
      throw new BusinessException(
          "ENTRY_UNSUPPORTED_FORMAT", "Hiện chỉ hỗ trợ hóa đơn điện tử .xml");
    }
    var result = extractor.extract(content); // ném lỗi trước khi lưu file nếu XML không hợp lệ
    var stored = storage.save(content, fileName, contentType, "ENTRY", user.id());
    var invoice = result.invoice();
    var partner = lookupPartner(user.companyId(), invoice.sellerTaxCode());
    var entries = defaultEntries(invoice);
    var draftId = UUID.randomUUID();
    var validation = validate(draftId, user.companyId(), invoice, entries);

    var draft =
        new EntryDraft(
            user.companyId(),
            SourceType.XML,
            stored.id(),
            "PURCHASE_INVOICE",
            toMap(new DraftData(invoice, partner)),
            new LinkedHashMap<>(result.confidence()),
            toList(entries),
            toList(validation),
            user.id());
    var saved = drafts.save(draft);
    audit(user, "ENTRY_DRAFT_CREATED", saved.getId(), Map.of("source", "XML"));
    return response(saved);
  }

  @Transactional(readOnly = true)
  public Page<EntryDraftResponse> list(CurrentUser user, Pageable pageable) {
    var page =
        user.role() == Role.ACCOUNTANT
            ? drafts.findByCompanyIdAndCreatedByAndStatusNot(
                user.companyId(), user.id(), Status.DISCARDED, pageable)
            : drafts.findByCompanyIdAndStatusNot(user.companyId(), Status.DISCARDED, pageable);
    return page.map(this::response);
  }

  @Transactional(readOnly = true)
  public EntryDraftResponse get(CurrentUser user, UUID id) {
    return response(find(user, id));
  }

  @Transactional
  public EntryDraftResponse update(CurrentUser user, UUID id, UpdateRequest req) {
    var d = editable(user, id);
    var data = readData(d);
    var invoice = req.invoice() != null ? req.invoice() : data.invoice();
    var partner =
        req.invoice() != null
                && !java.util.Objects.equals(
                    req.invoice().sellerTaxCode(), data.invoice().sellerTaxCode())
            ? lookupPartner(user.companyId(), invoice.sellerTaxCode())
            : data.partnerMatch();
    var entries = req.suggestedEntries() != null ? req.suggestedEntries() : readEntries(d);
    var validation = validate(d.getId(), user.companyId(), invoice, entries);
    d.update(
        toMap(new DraftData(invoice, partner)), toList(entries), toList(validation), Status.DRAFT);
    audit(user, "ENTRY_DRAFT_UPDATED", d.getId(), Map.of());
    return response(d);
  }

  /** Chạy lại rule; không có lỗi mức ERROR thì chuyển VALIDATED. */
  @Transactional
  public EntryDraftResponse validate(CurrentUser user, UUID id) {
    var d = editable(user, id);
    var validation = validate(d.getId(), user.companyId(), readData(d).invoice(), readEntries(d));
    var status = hasError(validation) ? Status.DRAFT : Status.VALIDATED;
    d.update(d.getExtractedData(), d.getSuggestedEntries(), toList(validation), status);
    return response(d);
  }

  /**
   * noRollbackFor: khi bị từ chối vì còn lỗi, việc đưa nháp về DRAFT (kèm kết quả kiểm tra mới) vẫn
   * phải được lưu dù ném BusinessException.
   */
  @Transactional(noRollbackFor = BusinessException.class)
  public EntryDraftResponse confirm(CurrentUser user, UUID id) {
    var d = find(user, id);
    if (d.getStatus() != Status.VALIDATED) {
      throw new BusinessException(
          "ENTRY_NOT_VALIDATED", "Chỉ xác nhận được nháp đã kiểm tra hợp lệ", HttpStatus.CONFLICT);
    }
    // kiểm tra lại ngay trước khi chốt (ví dụ hóa đơn trùng vừa xuất hiện trong ERP)
    var validation = validate(d.getId(), user.companyId(), readData(d).invoice(), readEntries(d));
    if (hasError(validation)) {
      d.update(d.getExtractedData(), d.getSuggestedEntries(), toList(validation), Status.DRAFT);
      throw new BusinessException("ENTRY_HAS_ERRORS", "Nháp còn lỗi mức ERROR, không thể xác nhận");
    }
    d.update(d.getExtractedData(), d.getSuggestedEntries(), toList(validation), Status.CONFIRMED);
    audit(user, "ENTRY_DRAFT_CONFIRMED", d.getId(), Map.of());
    return response(d);
  }

  @Transactional
  public void discard(CurrentUser user, UUID id) {
    var d = editable(user, id);
    d.setStatus(Status.DISCARDED);
    audit(user, "ENTRY_DRAFT_DISCARDED", d.getId(), Map.of());
  }

  // ---------- nội bộ ----------

  record DraftData(ExtractedInvoice invoice, PartnerMatch partnerMatch) {}

  private PartnerMatch lookupPartner(String companyId, String taxCode) {
    if (taxCode == null || taxCode.isBlank()) {
      return new PartnerMatch(null, null, null);
    }
    try {
      return erp.findPartnerByTaxCode(companyId, taxCode)
          .map(p -> new PartnerMatch(true, p.code(), p.name()))
          .orElse(new PartnerMatch(false, null, null));
    } catch (BusinessException e) {
      return new PartnerMatch(null, null, null); // ERP không khả dụng: không chặn nhập liệu
    }
  }

  /** Gợi ý định khoản mặc định (TT133) — độ tin cậy thấp, kế toán phải xác nhận. */
  static List<SuggestedEntry> defaultEntries(ExtractedInvoice invoice) {
    var out = new ArrayList<SuggestedEntry>();
    for (var l : invoice.lines()) {
      out.add(
          new SuggestedEntry(
              DEFAULT_DEBIT,
              DEFAULT_CREDIT,
              str(l.amount()),
              "Mua hàng hóa nhập kho (mặc định, cần xác nhận)",
              0.5));
    }
    if (invoice.totalVat() != null && invoice.totalVat().signum() > 0) {
      out.add(
          new SuggestedEntry(
              DEFAULT_VAT_DEBIT,
              DEFAULT_CREDIT,
              str(invoice.totalVat()),
              "Thuế GTGT được khấu trừ (mặc định, cần xác nhận)",
              0.5));
    }
    return out;
  }

  private List<ValidationItem> validate(
      UUID draftId, String companyId, ExtractedInvoice invoice, List<SuggestedEntry> entries) {
    var voucher = toVoucher(draftId, companyId, invoice, entries);
    var items = new ArrayList<ValidationItem>();
    RuleCheckService.Evaluation evaluation;
    try {
      evaluation = rules.dryRun(voucher, true);
    } catch (BusinessException e) {
      if (!e.getCode().startsWith("ERP_")) {
        throw e;
      }
      evaluation = rules.dryRun(voucher, false);
      items.add(
          new ValidationItem(
              "ERP_UNAVAILABLE",
              "WARNING",
              "Không kiểm tra được trùng hóa đơn vì ERP không khả dụng"));
    }
    evaluation
        .results()
        .forEach(r -> items.add(new ValidationItem(r.ruleId(), r.severity().name(), r.message())));
    return items;
  }

  /** Dựng chứng từ canonical từ nháp để chạy rule (cùng engine với chứng từ ERP). */
  static ErpVoucher toVoucher(
      UUID draftId, String companyId, ExtractedInvoice inv, List<SuggestedEntry> entries) {
    var lines = new ArrayList<ErpJournalLine>();
    for (int i = 0; i < inv.lines().size(); i++) {
      var l = inv.lines().get(i);
      var e = i < entries.size() ? entries.get(i) : null;
      lines.add(
          new ErpJournalLine(
              l.lineNo(),
              l.description(),
              e == null ? null : e.debitAccount(),
              e == null ? null : e.creditAccount(),
              orZero(l.amount()),
              orZero(l.amount()),
              null,
              orZero(l.quantity()),
              orZero(l.unitPrice()),
              l.vatRate() == null ? BigDecimal.valueOf(-1) : l.vatRate(),
              orZero(l.vatAmount()),
              null));
    }
    var invoice =
        new ErpInvoice(
            inv.invoiceTemplate(),
            inv.invoiceSeries(),
            inv.invoiceNo(),
            inv.invoiceDate(),
            inv.sellerTaxCode(),
            inv.sellerName(),
            orZero(inv.totalBeforeTax()),
            orZero(inv.totalVat()),
            BigDecimal.ZERO,
            orZero(inv.totalPayable()),
            inv.paymentMethod());
    return new ErpVoucher(
        draftId.toString(),
        companyId,
        ErpEntityType.PURCHASE_INVOICE,
        210,
        inv.invoiceNo(),
        inv.invoiceDate(),
        inv.invoiceDate(),
        null,
        new ErpPartnerRef(null, null, inv.sellerName(), inv.sellerTaxCode()),
        inv.currency(),
        orZero(inv.exchangeRate()),
        orZero(inv.totalPayable()),
        orZero(inv.totalVat()),
        BigDecimal.ZERO,
        false,
        lines,
        invoice);
  }

  private static boolean hasError(List<ValidationItem> items) {
    return items.stream().anyMatch(i -> "ERROR".equals(i.severity()));
  }

  private EntryDraft editable(CurrentUser user, UUID id) {
    var d = find(user, id);
    if (d.getStatus() == Status.CONFIRMED || d.getStatus() == Status.DISCARDED) {
      throw new BusinessException(
          "ENTRY_NOT_EDITABLE",
          "Nháp đã " + d.getStatus() + ", không thể sửa",
          HttpStatus.CONFLICT);
    }
    return d;
  }

  /** Kế toán viên chỉ thấy nháp của mình; kế toán trưởng/admin thấy cả công ty. */
  private EntryDraft find(CurrentUser user, UUID id) {
    var d =
        drafts
            .findByIdAndCompanyId(id, user.companyId())
            .filter(x -> x.getStatus() != Status.DISCARDED)
            .filter(x -> user.role() != Role.ACCOUNTANT || x.getCreatedBy().equals(user.id()))
            .orElseThrow(
                () ->
                    new BusinessException(
                        "ENTRY_DRAFT_NOT_FOUND", "Không tìm thấy nháp", HttpStatus.NOT_FOUND));
    return d;
  }

  private EntryDraftResponse response(EntryDraft d) {
    var data = readData(d);
    return new EntryDraftResponse(
        d.getId(),
        d.getSourceType().name(),
        d.getStatus().name(),
        data.invoice(),
        data.partnerMatch(),
        d.getFieldConfidence(),
        readEntries(d),
        mapper.convertValue(d.getValidation(), new TypeReference<List<ValidationItem>>() {}),
        d.getCreatedAt(),
        d.getUpdatedAt());
  }

  private DraftData readData(EntryDraft d) {
    return mapper.convertValue(d.getExtractedData(), DraftData.class);
  }

  private List<SuggestedEntry> readEntries(EntryDraft d) {
    return mapper.convertValue(
        d.getSuggestedEntries(), new TypeReference<List<SuggestedEntry>>() {});
  }

  private Map<String, Object> toMap(Object o) {
    return mapper.convertValue(o, new TypeReference<Map<String, Object>>() {});
  }

  private List<Map<String, Object>> toList(Object o) {
    return mapper.convertValue(o, new TypeReference<List<Map<String, Object>>>() {});
  }

  private void audit(CurrentUser user, String action, UUID id, Map<String, Object> detail) {
    events.publishEvent(
        new AuditEvent(
            ActorType.USER,
            user.id().toString(),
            user.companyId(),
            action,
            "entry_draft",
            id.toString(),
            detail));
  }

  private static BigDecimal orZero(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static String str(BigDecimal v) {
    return v == null ? "0" : v.toPlainString();
  }
}
