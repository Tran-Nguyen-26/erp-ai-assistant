package vn.edu.uet.erpai.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import vn.edu.uet.erpai.common.exception.BusinessException;
import vn.edu.uet.erpai.entry.extractor.EInvoiceXmlExtractor;

class EInvoiceXmlExtractorTest {

  private final EInvoiceXmlExtractor extractor = new EInvoiceXmlExtractor();

  private static byte[] fixture(String name) throws IOException {
    return new ClassPathResource("fixtures/einvoice/" + name).getInputStream().readAllBytes();
  }

  @Test
  void extractsHeaderPartiesTotalsAndLinesFromTt78Xml() throws Exception {
    var r = extractor.extract(fixture("valid-tt78.xml"));
    var inv = r.invoice();

    assertThat(inv.invoiceSeries()).isEqualTo("C26TAA");
    assertThat(inv.invoiceNo()).isEqualTo("0000123");
    assertThat(inv.invoiceDate()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(inv.sellerTaxCode()).isEqualTo("0101234567");
    assertThat(inv.sellerName()).isEqualTo("Công ty TNHH Alpha");
    assertThat(inv.buyerTaxCode()).isEqualTo("0109876543");
    assertThat(inv.totalPayable()).isEqualByComparingTo("13000000");
    assertThat(inv.lines()).hasSize(2);
    assertThat(inv.lines().get(0).vatRate()).isEqualByComparingTo("10");
    assertThat(inv.lines().get(0).vatAmount()).isEqualByComparingTo("1000000"); // tính từ thuế suất
    assertThat(inv.lines().get(1).vatRate()).isNull(); // KCT
    assertThat(inv.lines().get(1).vatLabel()).isEqualTo("KCT");
    assertThat(r.confidence()).containsEntry("invoiceNo", 1.0).containsEntry("lines", 1.0);
  }

  @Test
  void missingFieldsGetZeroConfidenceInsteadOfFailing() {
    var xml = "<HDon><DLHDon><TTChung><SHDon>1</SHDon></TTChung></DLHDon></HDon>";
    var r = extractor.extract(xml.getBytes(StandardCharsets.UTF_8));
    assertThat(r.confidence())
        .containsEntry("invoiceNo", 1.0)
        .containsEntry("sellerTaxCode", 0.0)
        .containsEntry("invoiceDate", 0.0)
        .containsEntry("lines", 0.0);
    assertThat(r.invoice().currency()).isEqualTo("VND");
  }

  @Test
  void rejectsXxeNonXmlAndNonInvoice() throws Exception {
    assertThatThrownBy(() -> extractor.extract(fixture("xxe.xml")))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("ENTRY_XML_INVALID"));
    assertThatThrownBy(() -> extractor.extract("không phải xml".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("ENTRY_XML_INVALID"));
    assertThatThrownBy(() -> extractor.extract("<a><b/></a>".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getCode()).isEqualTo("ENTRY_XML_NOT_INVOICE"));
  }
}
