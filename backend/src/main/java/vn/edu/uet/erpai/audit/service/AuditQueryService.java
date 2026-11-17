package vn.edu.uet.erpai.audit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.common.web.PageResponse;

/** Đọc audit_log (chỉ đọc). Luôn lọc theo công ty của người dùng. */
@Service
public class AuditQueryService {

  public record Filter(
      ActorType actorType,
      String actorId,
      String action,
      String targetType,
      String targetId,
      LocalDate from,
      LocalDate to) {}

  public record Actor(String id, String username) {}

  public record AuditLogResponse(
      long id,
      OffsetDateTime occurredAt,
      String actorType,
      Actor actor,
      String action,
      String targetType,
      String targetId,
      Map<String, Object> detail,
      String traceId) {}

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public AuditQueryService(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public PageResponse<AuditLogResponse> search(String companyId, Filter f, int page, int size) {
    int s = Math.min(Math.max(size, 1), 100);
    int p = Math.max(page, 0);
    var where = new StringBuilder(" WHERE a.company_id = ?");
    var args = new ArrayList<Object>(List.of(companyId));
    add(where, args, "a.actor_type", f.actorType() == null ? null : f.actorType().name());
    add(where, args, "a.actor_id", f.actorId());
    add(where, args, "a.action", f.action());
    add(where, args, "a.target_type", f.targetType());
    add(where, args, "a.target_id", f.targetId());
    if (f.from() != null) {
      where.append(" AND a.occurred_at >= ?");
      args.add(f.from().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime());
    }
    if (f.to() != null) {
      where.append(" AND a.occurred_at < ?");
      args.add(f.to().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime());
    }
    long total =
        jdbc.queryForObject("SELECT count(*) FROM audit_log a" + where, Long.class, args.toArray());
    var pageArgs = new ArrayList<>(args);
    pageArgs.add(s);
    pageArgs.add((long) p * s);
    var items =
        jdbc.query(
            "SELECT a.id, a.occurred_at, a.actor_type, a.actor_id, u.username, a.action, a.target_type,"
                + " a.target_id, a.detail::text AS detail, a.trace_id FROM audit_log a"
                + " LEFT JOIN app_user u ON u.id::text = a.actor_id"
                + where
                + " ORDER BY a.occurred_at DESC, a.id DESC LIMIT ? OFFSET ?",
            (rs, i) ->
                new AuditLogResponse(
                    rs.getLong("id"),
                    rs.getObject("occurred_at", OffsetDateTime.class),
                    rs.getString("actor_type"),
                    rs.getString("actor_id") == null
                        ? null
                        : new Actor(rs.getString("actor_id"), rs.getString("username")),
                    rs.getString("action"),
                    rs.getString("target_type"),
                    rs.getString("target_id"),
                    json(rs.getString("detail")),
                    rs.getString("trace_id")),
            pageArgs.toArray());
    return new PageResponse<>(items, p, s, total, (int) Math.ceil(total / (double) s));
  }

  private static void add(StringBuilder where, List<Object> args, String column, Object value) {
    if (value != null) {
      where.append(" AND ").append(column).append(" = ?");
      args.add(value);
    }
  }

  private Map<String, Object> json(String text) {
    try {
      return text == null ? Map.of() : mapper.readValue(text, new TypeReference<>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }
}
