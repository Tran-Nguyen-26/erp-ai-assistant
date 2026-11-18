package vn.edu.uet.erpai.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

/**
 * Một câu hỏi đánh giá truy xuất. Đoạn tài liệu được coi là ĐÚNG nếu trùng {@code docNumber} và có
 * {@code headingPath} bắt đầu bằng {@code headingPath} kỳ vọng (đoạn con của Điều/Khoản vẫn tính).
 * {@code answerable = false}: câu hỏi nằm ngoài kho tri thức, hệ thống phải trả lời "không đủ căn
 * cứ".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RagQuestion(
    String question, LocalDate asOf, boolean answerable, List<Expected> relevant) {

  public RagQuestion {
    relevant = relevant == null ? List.of() : List.copyOf(relevant);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Expected(String docNumber, String headingPath) {}
}
