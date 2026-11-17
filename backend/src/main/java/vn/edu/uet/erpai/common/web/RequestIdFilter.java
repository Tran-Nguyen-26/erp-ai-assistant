package vn.edu.uet.erpai.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Gán traceId (từ X-Request-Id hoặc tự sinh) vào MDC và header phản hồi. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    var id = req.getHeader(HEADER);
    if (id == null || id.isBlank() || id.length() > 64) {
      id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    MDC.put("traceId", id);
    res.setHeader(HEADER, id);
    try {
      chain.doFilter(req, res);
    } finally {
      MDC.remove("traceId");
    }
  }
}
