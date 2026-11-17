package vn.edu.uet.erpai.entry.extractor;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import vn.edu.uet.erpai.common.exception.BusinessException;

/**
 * Trích xuất hóa đơn điện tử XML theo cấu trúc Thông tư 78 ({@code HDon/DLHDon}). Parser chặn DTD
 * và thực thể ngoài (chống XXE). Kết quả tất định: không dùng LLM.
 */
@Component
public class EInvoiceXmlExtractor {

  public record Result(ExtractedInvoice invoice, Map<String, Double> confidence) {}

  public Result extract(byte[] xml) {
    var root = parse(xml);
    var data = child(root, "DLHDon");
    if (data == null) {
      throw new BusinessException(
          "ENTRY_XML_NOT_INVOICE", "File XML không phải hóa đơn điện tử (thiếu DLHDon)");
    }
    var general = child(data, "TTChung");
    var seller = path(data, "NDHDon", "NBan");
    var buyer = path(data, "NDHDon", "NMua");
    var total = path(data, "NDHDon", "TToan");

    var conf = new LinkedHashMap<String, Double>();
    var template = text(general, "KHMSHDon");
    var series = text(general, "KHHDon");
    var no = text(general, "SHDon");
    var date = date(text(general, "NLap"));
    var currency = orDefault(text(general, "DVTTe"), "VND");
    var rate = decimal(text(general, "TGia"));
    var sellerName = text(seller, "Ten");
    var sellerTax = text(seller, "MST");
    var buyerName = text(buyer, "Ten");
    var buyerTax = text(buyer, "MST");
    var beforeTax = decimal(text(total, "TgTCThue"));
    var vat = decimal(text(total, "TgTThue"));
    var payable = decimal(text(total, "TgTTTBSo"));
    var payment = text(general, "HTTToan");

    var lines = lines(path(data, "NDHDon", "DSHHDVu"));
    track(conf, "invoiceTemplate", template);
    track(conf, "invoiceSeries", series);
    track(conf, "invoiceNo", no);
    track(conf, "invoiceDate", date);
    track(conf, "sellerName", sellerName);
    track(conf, "sellerTaxCode", sellerTax);
    track(conf, "buyerTaxCode", buyerTax);
    track(conf, "totalBeforeTax", beforeTax);
    track(conf, "totalVat", vat);
    track(conf, "totalPayable", payable);
    conf.put("lines", lines.isEmpty() ? 0.0 : 1.0);

    return new Result(
        new ExtractedInvoice(
            template,
            series,
            no,
            date,
            sellerName,
            sellerTax,
            buyerName,
            buyerTax,
            currency,
            rate == null ? BigDecimal.ONE : rate,
            beforeTax,
            vat,
            payable,
            payment,
            lines),
        conf);
  }

  private List<ExtractedInvoice.Line> lines(Element list) {
    var out = new ArrayList<ExtractedInvoice.Line>();
    if (list == null) {
      return out;
    }
    int idx = 0;
    for (var item : children(list, "HHDVu")) {
      idx++;
      var amount = decimal(text(item, "ThTien"));
      var label = text(item, "TSuat");
      var rate = percent(label);
      // Thông tư 78 chỉ ghi thuế theo từng thuế suất (TToan), nên tiền thuế theo dòng là SỐ TÍNH
      // TOÁN.
      var vat =
          amount == null || rate == null
              ? null
              : amount.multiply(rate).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
      var stt = decimal(text(item, "STT"));
      out.add(
          new ExtractedInvoice.Line(
              stt == null ? idx : stt.intValue(),
              text(item, "THHDVu"),
              text(item, "DVTinh"),
              decimal(text(item, "SLuong")),
              decimal(text(item, "DGia")),
              amount,
              rate,
              label,
              vat));
    }
    return out;
  }

  // ---------- XML an toàn ----------

  private static Element parse(byte[] xml) {
    try {
      var f = DocumentBuilderFactory.newInstance();
      f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setFeature("http://xml.org/sax/features/external-general-entities", false);
      f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      f.setXIncludeAware(false);
      f.setExpandEntityReferences(false);
      var doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
      return doc.getDocumentElement();
    } catch (SAXException | java.io.IOException e) {
      throw new BusinessException(
          "ENTRY_XML_INVALID",
          "File XML không hợp lệ hoặc có cấu trúc bị cấm (DTD/thực thể ngoài)");
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Element child(Element parent, String name) {
    if (parent == null) {
      return null;
    }
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && local(e).equals(name)) {
        return e;
      }
    }
    return null;
  }

  private static List<Element> children(Element parent, String name) {
    var out = new ArrayList<Element>();
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && local(e).equals(name)) {
        out.add(e);
      }
    }
    return out;
  }

  private static Element path(Element start, String... names) {
    var cur = start;
    for (var n : names) {
      cur = child(cur, n);
      if (cur == null) {
        return null;
      }
    }
    return cur;
  }

  private static String local(Element e) {
    var n = e.getNodeName();
    return n.contains(":") ? n.substring(n.indexOf(':') + 1) : n;
  }

  private static String text(Element parent, String name) {
    var e = child(parent, name);
    if (e == null) {
      return null;
    }
    var t = e.getTextContent();
    return t == null || t.isBlank() ? null : t.strip();
  }

  // ---------- chuyển kiểu (sai định dạng → null, độ tin cậy 0) ----------

  private static BigDecimal decimal(String s) {
    if (s == null) {
      return null;
    }
    try {
      return new BigDecimal(s.replace(",", "").trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static BigDecimal percent(String label) {
    if (label == null) {
      return null;
    }
    var m = java.util.regex.Pattern.compile("^(\\d+(?:[.,]\\d+)?)\\s*%$").matcher(label.strip());
    return m.matches() ? new BigDecimal(m.group(1).replace(',', '.')) : null;
  }

  private static LocalDate date(String s) {
    if (s == null) {
      return null;
    }
    try {
      return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String orDefault(String v, String d) {
    return v == null ? d : v;
  }

  private static void track(Map<String, Double> conf, String field, Object value) {
    conf.put(field, value == null ? 0.0 : 1.0);
  }
}
