package vn.edu.uet.erpai.audit.service;

import java.util.Map;

/**
 * Sự kiện audit — các module khác publish qua {@code ApplicationEventPublisher} hoặc gọi {@link
 * AuditService#record}. Không chứa dữ liệu nhạy cảm (MST, số tài khoản) trong {@code detail}.
 */
public record AuditEvent(
    ActorType actorType,
    String actorId,
    String companyId,
    String action,
    String targetType,
    String targetId,
    Map<String, Object> detail) {}
