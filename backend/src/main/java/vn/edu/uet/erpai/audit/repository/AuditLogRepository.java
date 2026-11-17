package vn.edu.uet.erpai.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.uet.erpai.audit.domain.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
