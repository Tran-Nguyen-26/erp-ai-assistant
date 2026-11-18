package vn.edu.uet.erpai.entry.extractor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.common.llm.LlmClient;
import vn.edu.uet.erpai.common.llm.PromptSafety;
import vn.edu.uet.erpai.common.util.Prompts;

/**
 * Trích xuất hóa đơn từ văn bản/ảnh bằng LLM. CHỈ chạy với LLM local: ảnh hóa đơn không thể che
 * PII, và che MST/tên đối tác sẽ phá chính dữ liệu cần trích xuất (nguyên tắc #3). Kết quả luôn là
 * NHÁP kèm độ tin cậy từng trường; rule engine kiểm tra lại, người dùng xác nhận.
 */
@Component
public class LlmInvoiceExtractor {

  public static final int MAX_TEXT_CHARS = 20_000;

  private final LlmClient llm;
  private final String provider;
  private final Prompts.Prompt prompt = Prompts.load("entry-extract-invoice.st");

  public LlmInvoiceExtractor(LlmClient llm, @Value("${app.ai.provider:ollama}") String provider) {
    this.llm = llm;
    this.provider = provider;
  }

  public EInvoiceXmlExtractor.Result fromText(String text) {
    requireLocal();
    var clipped = text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    var out =
        llm.generate(
            prompt.body(),
            "<document>\n"
                + PromptSafety.data(clipped)
                + "\n</document>\nHãy trích xuất hóa đơn từ tài liệu trên.",
            LlmInvoice.class);
    return convert(out);
  }

  public EInvoiceXmlExtractor.Result fromImages(List<LlmClient.Image> images) {
    requireLocal();
    var out =
        llm.generateWithImages(
            prompt.body(), "Hãy trích xuất hóa đơn từ ảnh đính kèm.", images, LlmInvoice.class);
    return convert(out);
  }

  public String promptVersion() {
    return prompt.version();
  }

  private void requireLocal() {
    if (!"ollama".equalsIgnoreCase(provider)) {
      throw new BusinessException(
          "ENTRY_LLM_EXTRACTION_REQUIRES_LOCAL_LLM",
          "Trích xuất từ ảnh/PDF/văn bản chỉ chạy với LLM local (AI_PROVIDER=ollama) để dữ liệu hóa đơn không rời hệ thống",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
  }

  static EInvoiceXmlExtractor.Result convert(LlmInvoice out) {
    if (out == null) {
      throw new BusinessException(
          "ENTRY_EXTRACTION_FAILED",
          "Mô hình không trả về dữ liệu hóa đơn",
          HttpStatus.BAD_GATEWAY);
    }
    var lines = new ArrayList<ExtractedInvoice.Line>();
    int idx = 0;
    for (var l : out.lines() == null ? List.<LlmInvoice.Line>of() : out.lines()) {
      if (l == null) {
        continue;
      }
      idx++;
      var amount = VnParsing.number(l.amount());
      var rate = VnParsing.rate(l.vatRate());
      var vat =
          amount == null || rate.percent() == null
              ? null
              : amount
                  .multiply(rate.percent())
                  .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
      lines.add(
          new ExtractedInvoice.Line(
              idx,
              clean(l.description()),
              clean(l.unit()),
              VnParsing.number(l.quantity()),
              VnParsing.number(l.unitPrice()),
              amount,
              rate.percent(),
              rate.label(),
              vat));
    }
    var totalVat = VnParsing.number(out.totalVat());
    var invoice =
        new ExtractedInvoice(
            clean(out.invoiceTemplate()),
            clean(out.invoiceSeries()),
            clean(out.invoiceNo()),
            VnParsing.date(out.invoiceDate()),
            clean(out.sellerName()),
            digitsOrNull(out.sellerTaxCode()),
            clean(out.buyerName()),
            digitsOrNull(out.buyerTaxCode()),
            out.currency() == null || out.currency().isBlank()
                ? "VND"
                : out.currency().strip().toUpperCase(),
            BigDecimal.ONE,
            VnParsing.number(out.totalBeforeTax()),
            totalVat,
            VnParsing.number(out.totalPayable()),
            clean(out.paymentMethod()),
            lines);
    return new EInvoiceXmlExtractor.Result(
        invoice, ConfidenceScorer.score(invoice, out.confidence()));
  }

  private static String clean(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  /** MST giữ chữ số và dấu '-' (hậu tố đơn vị phụ thuộc); bỏ khoảng trắng/ký tự lạ do OCR. */
  private static String digitsOrNull(String s) {
    if (s == null) {
      return null;
    }
    var cleaned = s.replaceAll("[^0-9-]", "");
    return cleaned.isBlank() ? null : cleaned;
  }
}
