package vn.edu.uet.erpai.audit.service;

import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.erpai.audit.domain.AuditLog;
import vn.edu.uet.erpai.audit.repository.AuditLogRepository;

@Service
public class AuditService {

  private final AuditLogRepository repository;

  public AuditService(AuditLogRepository repository) {
    this.repository = repository;
  }

  /** Ghi trong transaction riêng để log vẫn tồn tại kể cả khi nghiệp vụ rollback. */
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditEvent e) {
    repository.save(
        new AuditLog(
            e.actorType(),
            e.actorId(),
            e.companyId(),
            e.action(),
            e.targetType(),
            e.targetId(),
            e.detail(),
            MDC.get("traceId"),
            null));
  }
}
