package vn.edu.uet.erpai.audit.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-model thống kê cho dashboard. Đọc trực tiếp bảng của các module khác bằng SQL chỉ-đọc (không
 * phụ thuộc lớp Java của chúng), luôn lọc theo công ty.
 */
@Service
public class DashboardService {

  private static final LocalDate MIN = LocalDate.of(1900, 1, 1);
  private static final LocalDate MAX = LocalDate.of(2999, 12, 31);

  private final JdbcTemplate jdbc;

  public DashboardService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> stats(String companyId, LocalDate from, LocalDate to) {
    var f = java.sql.Date.valueOf(from == null ? MIN : from);
    var t = java.sql.Date.valueOf(to == null ? MAX : to);
    var out = new LinkedHashMap<String, Object>();

    var bySeverity =
        counts(
            "SELECT severity, count(*) FROM violation WHERE company_id = ? AND (entity_date IS NULL OR entity_date BETWEEN ? AND ?) GROUP BY severity",
            companyId,
            f,
            t);
    var byStatus =
        counts(
            "SELECT status, count(*) FROM violation WHERE company_id = ? AND (entity_date IS NULL OR entity_date BETWEEN ? AND ?) GROUP BY status",
            companyId,
            f,
            t);
    long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
    long open =
        byStatus.getOrDefault("OPEN", 0L)
            + byStatus.getOrDefault("EXPLAINED", 0L)
            + byStatus.getOrDefault("PROPOSED", 0L);
    long resolved =
        byStatus.getOrDefault("RESOLVED", 0L) + byStatus.getOrDefault("RESOLVED_PENDING_ERP", 0L);
    out.put(
        "violations",
        Map.of("total", total, "open", open, "resolved", resolved, "bySeverity", bySeverity));

    out.put(
        "topRules",
        jdbc.query(
            "SELECT r.rule_id, r.name, count(*) AS c FROM violation v JOIN rule_definition r ON r.id = v.rule_definition_id"
                + " WHERE v.company_id = ? AND (v.entity_date IS NULL OR v.entity_date BETWEEN ? AND ?)"
                + " GROUP BY r.rule_id, r.name ORDER BY c DESC LIMIT 5",
            (rs, i) ->
                Map.of("ruleId", rs.getString(1), "name", rs.getString(2), "count", rs.getLong(3)),
            companyId,
            f,
            t));

    var p =
        counts(
            "SELECT status, count(*) FROM proposal WHERE company_id = ? AND created_at::date BETWEEN ? AND ? GROUP BY status",
            companyId,
            f,
            t);
    long approved = p.getOrDefault("APPROVED", 0L) + p.getOrDefault("EXPORTED", 0L);
    long rejected = p.getOrDefault("REJECTED", 0L);
    out.put(
        "proposals",
        Map.of(
            "pending",
            p.getOrDefault("PENDING", 0L),
            "approved",
            approved,
            "rejected",
            rejected,
            "acceptanceRate",
            approved + rejected == 0 ? 0.0 : round(approved / (double) (approved + rejected))));

    var ai =
        jdbc.queryForMap(
            "SELECT count(*) AS n, count(*) FILTER (WHERE e.support_status = 'SUPPORTED') AS supported,"
                + " coalesce(avg(e.latency_ms), 0) AS avg_latency FROM violation_explanation e"
                + " JOIN violation v ON v.id = e.violation_id WHERE v.company_id = ? AND e.created_at::date BETWEEN ? AND ?",
            companyId,
            f,
            t);
    long n = ((Number) ai.get("n")).longValue();
    var fb =
        jdbc.queryForMap(
            "SELECT count(*) AS n, count(*) FILTER (WHERE fb.rating = 1) AS pos FROM feedback fb"
                + " JOIN app_user u ON u.id = fb.user_id WHERE u.company_id = ? AND fb.created_at::date BETWEEN ? AND ?",
            companyId,
            f,
            t);
    long fbN = ((Number) fb.get("n")).longValue();
    out.put(
        "ai",
        Map.of(
            "explanations",
            n,
            "supportedRate",
            n == 0 ? 0.0 : round(((Number) ai.get("supported")).doubleValue() / n),
            "avgLatencyMs",
            Math.round(((Number) ai.get("avg_latency")).doubleValue()),
            "positiveFeedbackRate",
            fbN == 0 ? 0.0 : round(((Number) fb.get("pos")).doubleValue() / fbN)));

    var d =
        counts(
            "SELECT status, count(*) FROM entry_draft WHERE company_id = ? AND created_at::date BETWEEN ? AND ? GROUP BY status",
            companyId,
            f,
            t);
    out.put(
        "entry",
        Map.of(
            "drafts",
            d.values().stream().mapToLong(Long::longValue).sum(),
            "confirmed",
            d.getOrDefault("CONFIRMED", 0L),
            "avgLowConfidenceFields",
            0.0));
    return out;
  }

  private Map<String, Long> counts(String sql, Object... args) {
    var m = new LinkedHashMap<String, Long>();
    jdbc.query(
        sql,
        (rs) -> {
          m.put(rs.getString(1), rs.getLong(2));
        },
        args);
    return m;
  }

  private static double round(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}
