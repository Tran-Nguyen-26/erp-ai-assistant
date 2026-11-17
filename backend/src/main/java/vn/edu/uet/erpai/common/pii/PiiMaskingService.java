package vn.edu.uet.erpai.common.pii;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Che MST, số tài khoản và tên/địa chỉ đối tác trước khi gửi prompt tới LLM cloud (nguyên tắc #3).
 * Bảng ánh xạ chỉ tồn tại trong bộ nhớ của một lượt xử lý để khôi phục câu trả lời.
 */
@Service
public class PiiMaskingService {

  /**
   * MST: 10 số, 10-(1..3 số) hoặc 12 số (cá nhân); chấp nhận hậu tố sai vì dữ liệu lỗi vẫn là PII.
   */
  private static final Pattern TAX_CODE =
      Pattern.compile("(?<![\\d-])(\\d{10}-\\d{1,3}|\\d{12}|\\d{10})(?![\\d-])");

  /** Dãy 9–19 chữ số còn lại coi là số tài khoản ngân hàng. */
  private static final Pattern ACCOUNT = Pattern.compile("(?<!\\d)\\d{9,19}(?!\\d)");

  public record Masked(String text, Map<String, String> mapping) {

    /** Khôi phục giá trị gốc trong văn bản (ví dụ câu trả lời của LLM). */
    public String unmask(String masked) {
      if (masked == null) return null;
      var result = masked;
      for (var e : mapping.entrySet()) {
        result = result.replace(e.getKey(), e.getValue());
      }
      return result;
    }
  }

  /**
   * @param sensitiveStrings tên/địa chỉ đối tác cần che theo giá trị (so khớp nguyên văn)
   */
  public Masked mask(String text, Collection<String> sensitiveStrings) {
    var mapping = new LinkedHashMap<String, String>();
    var result = text;
    int n = 0;
    for (var s : sensitiveStrings) {
      if (s != null && s.length() >= 3 && result.contains(s)) {
        var token = "[[PARTNER_" + (++n) + "]]";
        mapping.put(token, s);
        result = result.replace(s, token);
      }
    }
    result = replace(result, TAX_CODE, "TAX", mapping);
    result = replace(result, ACCOUNT, "ACCT", mapping);
    return new Masked(result, mapping);
  }

  private static String replace(String text, Pattern p, String kind, Map<String, String> mapping) {
    var m = p.matcher(text);
    var sb = new StringBuilder();
    var tokens =
        new LinkedHashMap<String, String>(); // giá trị gốc → token (cùng giá trị cùng token)
    while (m.find()) {
      var original = m.group();
      var token = tokens.get(original);
      if (token == null) {
        token = "[[" + kind + "_" + (tokens.size() + 1) + "]]";
        tokens.put(original, token);
      }
      m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(token));
    }
    m.appendTail(sb);
    tokens.forEach((original, token) -> mapping.put(token, original));
    return sb.toString();
  }
}
