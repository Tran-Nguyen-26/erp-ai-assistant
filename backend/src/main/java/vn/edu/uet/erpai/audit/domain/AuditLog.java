package vn.edu.uet.erpai.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.uet.erpai.audit.service.ActorType;

@Entity
@Table(name = "audit_log")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt = OffsetDateTime.now();

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(name = "actor_type", nullable = false)
  private ActorType actorType;

  @Column(name = "actor_id")
  private String actorId;

  @Column(name = "company_id")
  private String companyId;

  @Column(nullable = false)
  private String action;

  @Column(name = "target_type")
  private String targetType;

  @Column(name = "target_id")
  private String targetId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> detail = Map.of();

  @Column(name = "trace_id")
  private String traceId;

  @Column(name = "ip_address")
  private String ipAddress;

  protected AuditLog() {}

  public AuditLog(
      ActorType actorType,
      String actorId,
      String companyId,
      String action,
      String targetType,
      String targetId,
      Map<String, Object> detail,
      String traceId,
      String ipAddress) {
    this.actorType = actorType;
    this.actorId = actorId;
    this.companyId = companyId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.detail = detail == null ? Map.of() : detail;
    this.traceId = traceId;
    this.ipAddress = ipAddress;
  }

  public Long getId() {
    return id;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public ActorType getActorType() {
    return actorType;
  }

  public String getActorId() {
    return actorId;
  }

  public String getCompanyId() {
    return companyId;
  }

  public String getAction() {
    return action;
  }

  public String getTargetType() {
    return targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public Map<String, Object> getDetail() {
    return detail;
  }

  public String getTraceId() {
    return traceId;
  }

  public String getIpAddress() {
    return ipAddress;
  }
}
