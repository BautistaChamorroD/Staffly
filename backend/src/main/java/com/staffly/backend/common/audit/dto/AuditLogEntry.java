package com.staffly.backend.common.audit.dto;

import com.staffly.backend.common.audit.AuditLog;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila-por-campo de {@code GET /audit-log} (RF-28 / AUD-34 / issue #155) —
 * misma forma que ya consume el historial de Employee (RF-06), sin agrupar
 * varios cambios de una misma operación en un solo evento.
 */
public record AuditLogEntry(
        UUID id,
        String entityType,
        UUID entityId,
        UUID usuarioId,
        String campo,
        String valorAnterior,
        String valorNuevo,
        Instant fecha) {

    public static AuditLogEntry from(AuditLog auditLog) {
        return new AuditLogEntry(
                auditLog.getId(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getUsuarioId(),
                auditLog.getCampo(),
                auditLog.getValorAnterior(),
                auditLog.getValorNuevo(),
                auditLog.getFecha());
    }
}
