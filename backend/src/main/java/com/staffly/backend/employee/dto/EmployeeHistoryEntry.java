package com.staffly.backend.employee.dto;

import com.staffly.backend.common.audit.AuditLog;

import java.time.Instant;
import java.util.UUID;

/**
 * Una entrada del historial de cambios de un empleado (RF-06): qué campo
 * cambió, de qué valor a cuál, quién lo cambió y cuándo.
 */
public record EmployeeHistoryEntry(
        String campo,
        String valorAnterior,
        String valorNuevo,
        UUID usuarioId,
        Instant fecha) {

    public static EmployeeHistoryEntry from(AuditLog auditLog) {
        return new EmployeeHistoryEntry(
                auditLog.getCampo(),
                auditLog.getValorAnterior(),
                auditLog.getValorNuevo(),
                auditLog.getUsuarioId(),
                auditLog.getFecha());
    }
}
