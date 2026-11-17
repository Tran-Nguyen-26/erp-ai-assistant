package vn.edu.uet.erpai.agent.workflow;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import vn.edu.uet.erpai.agent.domain.ViolationExplanation;
import vn.edu.uet.erpai.agent.domain.ViolationExplanation.SupportStatus;
import vn.edu.uet.erpai.agent.repository.ViolationExplanationRepository;
import vn.edu.uet.erpai.audit.service.ActorType;
import vn.edu.uet.erpai.audit.service.AuditEvent;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.llm.LlmClient;
import vn.edu.uet.erpai.common.pii.PiiMaskingService;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievalResult;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievedChunk;
import vn.edu.uet.erpai.knowledge.service.KnowledgeSearchService;
import vn.edu.uet.erpai.review.service.CreatorType;
import vn.edu.uet.erpai.review.service.ProposalResponse;
import vn.edu.uet.erpai.review.service.ProposalService;
import vn.edu.uet.erpai.rule.service.RuleAdminService;
import vn.edu.uet.erpai.rule.service.ViolationResponse;
import vn.edu.uet.erpai.rule.service.ViolationService;

/**
 * Pipeline cố định: load violation → load chứng từ → truy xuất tri thức → (đủ căn cứ?) → LLM giải
 * thích → kiểm tra trích dẫn → lưu. Rule đã quyết định vi phạm; LLM chỉ giải thích (nguyên tắc #1).
 */
@Service
public class ExplainViolationWorkflow {

  static final String PROMPT_RESOURCE = "prompts/agent-explain-violation.st";

  private final ViolationService violations;
  private final RuleAdminService rules;
  private final ErpQueryService erp;
  private final KnowledgeSearchService knowledge;
  private final LlmClient llm;
  private final PiiMaskingService masking;
  private final ViolationExplanationRepository explanations;
  private final ProposalService proposalService;
  private final ApplicationEventPublisher events;
  private final String provider;
  private final String systemPrompt;
  private final String promptVersion;

  public ExplainViolationWorkflow(
      ViolationService violations,
      RuleAdminService rules,
      ErpQueryService erp,
      KnowledgeSearchService knowledge,
      LlmClient llm,
      PiiMaskingService masking,
      ViolationExplanationRepository explanations,
      ProposalService proposalService,
      ApplicationEventPublisher events,
      @Value("${app.ai.provider:ollama}") String provider) {
    this.violations = violations;
    this.rules = rules;
    this.erp = erp;
    this.knowledge = knowledge;
    this.llm = llm;
    this.masking = masking;
    this.explanations = explanations;
    this.proposalService = proposalService;
    this.events = events;
    this.provider = provider;
    var raw = loadPrompt();
    int nl = raw.indexOf('\n');
    this.promptVersion = raw.substring(0, nl).replace("# version:", "").trim();
    this.systemPrompt = raw.substring(nl + 1).strip();
  }

  public ExplanationResponse explain(
      CurrentUser user, UUID violationId, boolean regenerate, boolean withProposal) {
    var violation = violations.get(user.companyId(), violationId);
    if (!regenerate) {
      var cached =
          explanations.findFirstByViolationIdAndPromptVersionOrderByCreatedAtDesc(
              violationId, promptVersion);
      if (cached.isPresent()) {
        var pending =
            withProposal
                ? proposalService.findPending(user.companyId(), violationId).orElse(null)
                : null;
        return respond(violationId, cached.get(), true, pending, null);
      }
    }
    long start = System.nanoTime();
    var rule = rules.get(violation.ruleId());
    ErpVoucher voucher = erp.getPurchaseInvoice(user.companyId(), violation.entityRef());
    LocalDate asOf = voucher.voucherDate() != null ? voucher.voucherDate() : LocalDate.now();

    var query = buildQuery(rule.ragQuery(), rule.legalRefs(), violation);
    var retrieved = knowledge.search(query, asOf, null);

    ViolationExplanation saved;
    ExplanationOutput.SuggestedFix fix = null;
    if (!retrieved.sufficient()) {
      saved = unsupported(violation, retrieved, start);
    } else {
      var generated = generate(violation, voucher, rule.legalRefs(), retrieved, start);
      saved = generated.explanation();
      fix = generated.fix();
    }
    saved = explanations.save(saved);
    violations.markExplained(user.companyId(), violationId);
    events.publishEvent(
        new AuditEvent(
            ActorType.AGENT,
            user.id().toString(),
            user.companyId(),
            "EXPLANATION_GENERATED",
            "violation",
            violationId.toString(),
            Map.of("supportStatus", saved.getSupportStatus().name(), "model", saved.getModel())));

    ProposalResponse proposal = null;
    String skipped = null;
    if (withProposal) {
      if (saved.getSupportStatus() == SupportStatus.UNSUPPORTED
          || fix == null
          || fix.changes() == null
          || fix.changes().isEmpty()) {
        skipped = "NO_SUGGESTION";
      } else {
        try {
          proposal =
              proposalService.create(
                  user,
                  violationId,
                  CreatorType.AGENT,
                  fix.changes().stream()
                      .map(c -> new ProposalService.ChangeRequest(c.path(), c.to()))
                      .toList(),
                  saved.getSummary(),
                  saved.getCitations());
        } catch (BusinessException e) {
          skipped =
              switch (e.getCode()) {
                case "PROPOSAL_VERIFICATION_FAILED" -> "VERIFICATION_FAILED";
                case "PROPOSAL_PATH_INVALID" -> "INVALID_PATH";
                default -> "NOT_PROPOSABLE";
              };
        }
      }
    }
    return respond(violationId, saved, false, proposal, skipped);
  }

  // ---------- các bước ----------

  private ViolationExplanation unsupported(
      ViolationResponse v, RetrievalResult retrieved, long startNanos) {
    return new ViolationExplanation(
        v.id(),
        v.message(),
        v.message() + "\n\nKhông tìm thấy căn cứ trong kho tài liệu để giải thích sâu hơn.",
        List.of(),
        SupportStatus.UNSUPPORTED,
        BigDecimal.ZERO,
        "rule-only",
        promptVersion,
        elapsed(startNanos));
  }

  private record Generated(ViolationExplanation explanation, ExplanationOutput.SuggestedFix fix) {}

  private Generated generate(
      ViolationResponse v,
      ErpVoucher voucher,
      List<String> legalRefs,
      RetrievalResult retrieved,
      long startNanos) {
    var passages = retrieved.chunks();
    var user = userPrompt(v, voucher, legalRefs, passages);
    PiiMaskingService.Masked masked = null;
    var promptText = user;
    if (!"ollama".equalsIgnoreCase(provider)) {
      var sensitive = new ArrayList<String>();
      if (voucher.partner() != null) {
        sensitive.add(voucher.partner().name());
      }
      masked = masking.mask(user, sensitive);
      promptText = masked.text();
    }
    var out = llm.generate(systemPrompt, promptText, ExplanationOutput.class);

    var summary = unmask(masked, out.summary());
    var explanation = unmask(masked, out.explanation());
    var valid = validCitations(out.citations(), passages);
    int claimed = out.citations() == null ? 0 : out.citations().size();

    SupportStatus status;
    double confidence = out.confidence() == null ? 0.5 : Math.max(0, Math.min(1, out.confidence()));
    if (valid.isEmpty()) {
      status = SupportStatus.UNSUPPORTED;
      confidence = Math.min(confidence, 0.3);
    } else if (valid.size() < claimed) {
      status = SupportStatus.PARTIAL;
    } else {
      status = SupportStatus.SUPPORTED;
    }
    var citations = new ArrayList<Map<String, Object>>();
    for (var c : valid) {
      var m = new LinkedHashMap<String, Object>();
      m.put("index", c.index());
      m.put("chunkId", c.chunk().chunkId().toString());
      m.put("documentId", c.chunk().documentId().toString());
      m.put("docNumber", c.chunk().docNumber());
      m.put("headingPath", c.chunk().headingPath());
      m.put("quote", c.quote());
      m.put(
          "effectiveFrom",
          c.chunk().effectiveFrom() == null ? null : c.chunk().effectiveFrom().toString());
      citations.add(m);
    }
    var explanationEntity =
        new ViolationExplanation(
            v.id(),
            blank(summary) ? v.message() : summary,
            blank(explanation) ? v.message() : explanation,
            citations,
            status,
            BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP),
            llm.modelName(),
            promptVersion,
            elapsed(startNanos));
    return new Generated(explanationEntity, out.suggestedFix());
  }

  private record ValidCitation(int index, RetrievedChunk chunk, String quote) {}

  /**
   * Giữ citation có index thuộc tập đã truy xuất và quote là chuỗi con của chunk (sau chuẩn hóa).
   */
  private static List<ValidCitation> validCitations(
      List<ExplanationOutput.CitationOut> citations, List<RetrievedChunk> passages) {
    var out = new ArrayList<ValidCitation>();
    var seen = new HashSet<Integer>();
    if (citations == null) return out;
    for (var c : citations) {
      if (c == null || c.index() == null || c.quote() == null || c.quote().isBlank()) continue;
      int i = c.index();
      if (i < 1 || i > passages.size() || !seen.add(i)) continue;
      var chunk = passages.get(i - 1);
      if (normalize(chunk.content()).contains(normalize(c.quote()))) {
        out.add(new ValidCitation(i, chunk, c.quote().strip()));
      }
    }
    return out;
  }

  private static String normalize(String s) {
    return Normalizer.normalize(s, Normalizer.Form.NFC)
        .toLowerCase()
        .replaceAll("\\s+", " ")
        .strip();
  }

  /** Dữ liệu không tin cậy luôn nằm trong delimiter; không bao giờ nối vào system prompt. */
  private static String userPrompt(
      ViolationResponse v,
      ErpVoucher voucher,
      List<String> legalRefs,
      List<RetrievedChunk> passages) {
    var sb = new StringBuilder();
    sb.append("<violation>\n")
        .append("Mã quy tắc: ")
        .append(v.ruleId())
        .append('\n')
        .append("Tên quy tắc: ")
        .append(v.ruleName())
        .append('\n')
        .append("Mức độ: ")
        .append(v.severity())
        .append('\n')
        .append("Thông điệp: ")
        .append(v.message())
        .append('\n')
        .append("Bằng chứng: ")
        .append(v.evidence())
        .append('\n')
        .append("</violation>\n");
    sb.append("<erp_data>\n")
        .append("Số chứng từ: ")
        .append(voucher.voucherNo())
        .append('\n')
        .append("Ngày chứng từ: ")
        .append(voucher.voucherDate())
        .append('\n')
        .append("Diễn giải: ")
        .append(voucher.description())
        .append('\n');
    if (voucher.partner() != null) {
      sb.append("Đối tượng: ")
          .append(voucher.partner().name())
          .append(" (MST ")
          .append(voucher.partner().taxCode())
          .append(")\n");
    }
    sb.append("</erp_data>\n");
    if (!legalRefs.isEmpty()) {
      sb.append("Căn cứ liên quan: ").append(String.join("; ", legalRefs)).append('\n');
    }
    sb.append("<passages>\n");
    for (int i = 0; i < passages.size(); i++) {
      var p = passages.get(i);
      sb.append('[')
          .append(i + 1)
          .append("] ")
          .append(p.docNumber() == null ? p.docTitle() : p.docNumber())
          .append(" — ")
          .append(p.headingPath())
          .append(":\n")
          .append(p.content())
          .append("\n\n");
    }
    sb.append("</passages>\nHãy giải thích sai lệch trên theo đúng định dạng yêu cầu.");
    return sb.toString();
  }

  private static String buildQuery(String ragQuery, List<String> legalRefs, ViolationResponse v) {
    var base = ragQuery == null || ragQuery.isBlank() ? v.ruleName() : ragQuery;
    return legalRefs.isEmpty() ? base : base + " " + String.join(" ", legalRefs);
  }

  private ExplanationResponse respond(
      UUID violationId,
      ViolationExplanation e,
      boolean cached,
      ProposalResponse proposal,
      String proposalSkippedReason) {
    var citations = new ArrayList<ExplanationResponse.Citation>();
    for (var m : e.getCitations()) {
      citations.add(
          new ExplanationResponse.Citation(
              ((Number) m.get("index")).intValue(),
              UUID.fromString((String) m.get("chunkId")),
              UUID.fromString((String) m.get("documentId")),
              (String) m.get("docNumber"),
              (String) m.get("headingPath"),
              (String) m.get("quote"),
              m.get("effectiveFrom") == null
                  ? null
                  : LocalDate.parse((String) m.get("effectiveFrom"))));
    }
    return new ExplanationResponse(
        violationId,
        new ExplanationResponse.Explanation(
            e.getId(),
            e.getSummary(),
            e.getExplanation(),
            citations,
            e.getSupportStatus().name(),
            e.getConfidence(),
            e.getModel(),
            e.getPromptVersion(),
            cached,
            e.getCreatedAt()),
        proposal,
        proposalSkippedReason);
  }

  private static String unmask(PiiMaskingService.Masked masked, String text) {
    return masked == null ? text : masked.unmask(text);
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static int elapsed(long startNanos) {
    return (int) ((System.nanoTime() - startNanos) / 1_000_000);
  }

  private static String loadPrompt() {
    try (var in = new ClassPathResource(PROMPT_RESOURCE).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Không đọc được prompt " + PROMPT_RESOURCE, e);
    }
  }
}
