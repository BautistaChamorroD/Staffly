package com.staffly.backend.common.audit;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer del registro de auditoría: persiste cada
 * AuditableFieldChangedEvent como un row de audit_log. Corre sincrónico
 * dentro de la transacción del servicio que publicó el evento — el historial
 * se guarda o se revierte junto con el cambio que lo originó.
 */
@Component
public class AuditLogEventListener {

    private final AuditLogRepository auditLogRepository;

    public AuditLogEventListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @EventListener
    public void onAuditableFieldChanged(AuditableFieldChangedEvent event) {
        AuditLog entry = new AuditLog();
        entry.setCompanyId(event.companyId());
        entry.setEntityType(event.entityType());
        entry.setEntityId(event.entityId());
        entry.setUsuarioId(event.usuarioId());
        entry.setCampo(event.campo());
        entry.setValorAnterior(event.valorAnterior());
        entry.setValorNuevo(event.valorNuevo());
        auditLogRepository.save(entry);
    }
}
