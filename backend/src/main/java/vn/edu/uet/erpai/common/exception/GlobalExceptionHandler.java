package vn.edu.uet.erpai.common.exception;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, WebRequest req) {
    var response = build(ex.getStatus(), ex.getCode(), ex.getMessage(), req, List.of());
    ex.getProperties().forEach((k, v) -> response.getBody().setProperty(k, v));
    return response;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException ex, WebRequest req) {
    var errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                f ->
                    Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
            .toList();
    return build(
        HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Dữ liệu không hợp lệ", req, errors);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleDenied(AccessDeniedException ex, WebRequest req) {
    return build(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "Không đủ quyền", req, List.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleOther(Exception ex, WebRequest req) {
    log.error("Lỗi không xử lý được", ex);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Lỗi hệ thống", req, List.of());
  }

  private ResponseEntity<ProblemDetail> build(
      HttpStatus status, String code, String detail, WebRequest req, List<?> errors) {
    var pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setType(URI.create("https://erpai.local/errors/" + code.toLowerCase().replace('_', '-')));
    pd.setTitle(status.getReasonPhrase());
    if (req instanceof ServletWebRequest swr) {
      pd.setInstance(URI.create(swr.getRequest().getRequestURI()));
    }
    pd.setProperty("code", code);
    pd.setProperty("traceId", MDC.get("traceId"));
    if (!errors.isEmpty()) {
      pd.setProperty("errors", errors);
    }
    return ResponseEntity.status(status).body(pd);
  }
}
