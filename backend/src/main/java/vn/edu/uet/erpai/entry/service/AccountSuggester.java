package vn.edu.uet.erpai.entry.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.llm.LlmClient;
import vn.edu.uet.erpai.common.llm.PromptSafety;
import vn.edu.uet.erpai.common.util.Prompts;
import vn.edu.uet.erpai.entry.extractor.ExtractedInvoice;
import vn.edu.uet.erpai.entry.service.EntryDraftResponse.SuggestedEntry;
import vn.edu.uet.erpai.rule.service.ReferenceDataProvider;

/**
 * Gợi ý định khoản. Mặc định tất định (hàng hóa Nợ 1561/156, thuế Nợ 1331, Có 331). Khi có LLM
 * local và danh mục tài khoản của công ty, LLM chọn tài khoản Nợ cho từng dòng — nhưng chỉ chấp
 * nhận tài khoản có trong danh mục (kiểm tra lại ở đây); trái lại giữ mặc định. Luôn có độ tin cậy
 * thấp.
 */
@Component
public class AccountSuggester {

  private static final Logger log = LoggerFactory.getLogger(AccountSuggester.class);
  private static final double LLM_CAP = 0.7;
  private static final double DEFAULT_CONFIDENCE = 0.5;
  private static final int MAX_CANDIDATES = 120;
  private static final String CREDIT = "331";

  public record Choices(List<Choice> lines) {}

  public record Choice(Integer lineNo, String debitAccount, String reason, Double confidence) {}

  private final LlmClient llm;
  private final ReferenceDataProvider reference;
  private final String provider;
  private final Prompts.Prompt prompt = Prompts.load("entry-suggest-accounts.st");

  public AccountSuggester(
      LlmClient llm,
      ReferenceDataProvider reference,
      @Value("${app.ai.provider:ollama}") String provider) {
    this.llm = llm;
    this.reference = reference;
    this.provider = provider;
  }

  public List<SuggestedEntry> suggest(String companyId, ExtractedInvoice invoice) {
    var chart = reference.forCompany(companyId);
    var candidates = candidates(chart.detailAccounts(), chart.accountNames());
    var defaultDebit =
        chart.detailAccounts().contains("1561") || chart.detailAccounts().isEmpty()
            ? "1561"
            : "156";
    var vatDebit =
        chart.detailAccounts().isEmpty() || chart.detailAccounts().contains("1331")
            ? "1331"
            : "133";
    Map<Integer, Choice> chosen = Map.of();
    if ("ollama".equalsIgnoreCase(provider)
        && !candidates.isEmpty()
        && !invoice.lines().isEmpty()) {
      chosen = askLlm(invoice, candidates, chart.accountNames());
    }

    var out = new ArrayList<SuggestedEntry>();
    for (var l : invoice.lines()) {
      var c = chosen.get(l.lineNo());
      if (c != null) {
        double conf =
            Math.min(
                c.confidence() == null ? DEFAULT_CONFIDENCE : Math.max(0, c.confidence()), LLM_CAP);
        out.add(
            new SuggestedEntry(
                c.debitAccount(), CREDIT, str(l.amount()), "AI gợi ý: " + clip(c.reason()), conf));
      } else {
        out.add(
            new SuggestedEntry(
                defaultDebit,
                CREDIT,
                str(l.amount()),
                "Mua hàng hóa nhập kho (mặc định, cần xác nhận)",
                DEFAULT_CONFIDENCE));
      }
    }
    if (invoice.totalVat() != null && invoice.totalVat().signum() > 0) {
      out.add(
          new SuggestedEntry(
              vatDebit,
              CREDIT,
              str(invoice.totalVat()),
              "Thuế GTGT được khấu trừ (mặc định, cần xác nhận)",
              DEFAULT_CONFIDENCE));
    }
    return out;
  }

  private Map<Integer, Choice> askLlm(
      ExtractedInvoice invoice, List<String> candidates, Map<String, String> names) {
    var accounts =
        candidates.stream()
            .map(a -> a + " - " + names.getOrDefault(a, ""))
            .collect(Collectors.joining("\n"));
    var lines = new StringBuilder();
    invoice
        .lines()
        .forEach(
            l ->
                lines
                    .append(l.lineNo())
                    .append(". ")
                    .append(l.description())
                    .append(" (")
                    .append(str(l.amount()))
                    .append(")\n"));
    try {
      var result =
          llm.generate(
              prompt.body(),
              "<accounts>\n"
                  + PromptSafety.data(accounts)
                  + "\n</accounts>\n<lines>\n"
                  + PromptSafety.data(lines)
                  + "</lines>\nChọn tài khoản Nợ cho từng dòng.",
              Choices.class);
      var valid = new java.util.HashMap<Integer, Choice>();
      if (result != null && result.lines() != null) {
        for (var c : result.lines()) {
          // chỉ nhận tài khoản có trong danh mục: không tin LLM về mã tài khoản
          if (c != null
              && c.lineNo() != null
              && c.debitAccount() != null
              && candidates.contains(c.debitAccount().strip())) {
            valid.put(
                c.lineNo(),
                new Choice(c.lineNo(), c.debitAccount().strip(), c.reason(), c.confidence()));
          }
        }
      }
      return valid;
    } catch (RuntimeException e) {
      log.warn("Không gợi ý được tài khoản bằng LLM, dùng mặc định: {}", e.getMessage());
      return Map.of();
    }
  }

  /** Tài khoản chi tiết thuộc nhóm tài sản/chi phí có thể là Nợ của hóa đơn mua vào. */
  static List<String> candidates(java.util.Set<String> detail, Map<String, String> names) {
    return detail.stream()
        .filter(a -> !a.isEmpty() && "1268".indexOf(a.charAt(0)) >= 0)
        .filter(
            a ->
                !a.startsWith("111")
                    && !a.startsWith("112")
                    && !a.startsWith("133")
                    && !a.startsWith("131"))
        .sorted()
        .limit(MAX_CANDIDATES)
        .toList();
  }

  private static String clip(String s) {
    return s == null ? "" : s.length() > 80 ? s.substring(0, 80) : s;
  }

  private static String str(java.math.BigDecimal v) {
    return v == null ? "0" : v.toPlainString();
  }
}
