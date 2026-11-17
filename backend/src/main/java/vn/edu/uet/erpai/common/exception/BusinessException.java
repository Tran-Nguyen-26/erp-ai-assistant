package vn.edu.uet.erpai.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ. {@code code} dạng MODULE_REASON, ví dụ RULE_NOT_FOUND. */
public class BusinessException extends RuntimeException {

  private final String code;
  private final HttpStatus status;
  private final Map<String, Object> properties = new LinkedHashMap<>();

  public BusinessException(String code, String message, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public BusinessException(String code, String message) {
    this(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  /** Thuộc tính bổ sung đưa vào ProblemDetail (ví dụ kết quả kiểm chứng). */
  public BusinessException with(String key, Object value) {
    properties.put(key, value);
    return this;
  }

  public Map<String, Object> getProperties() {
    return properties;
  }

  public String getCode() {
    return code;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
