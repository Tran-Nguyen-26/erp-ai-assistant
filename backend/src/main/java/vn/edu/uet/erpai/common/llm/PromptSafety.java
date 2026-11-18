package vn.edu.uet.erpai.common.llm;

/**
 * Vô hiệu hóa dấu {@code <} và {@code >} trong dữ liệu không tin cậy (diễn giải chứng từ, văn bản
 * hóa đơn, nội dung tài liệu, câu hỏi…) trước khi đặt vào giữa các thẻ delimiter của prompt, để dữ
 * liệu không thể đóng thẻ sớm rồi chèn chỉ dẫn giả (nguyên tắc #8). Thay bằng ký tự full-width nhìn
 * gần giống nên nội dung vẫn đọc được.
 */
public final class PromptSafety {

  private PromptSafety() {}

  public static String data(Object value) {
    if (value == null) {
      return "";
    }
    return value.toString().replace('<', '＜').replace('>', '＞');
  }
}
