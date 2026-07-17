package com.staffly.backend.common.audit;

import java.util.UUID;

/**
 * Evento de dominio: un campo auditable de una entidad de negocio cambió de
 * valor (patrón Observer, ver docs/patrones-diseno.md). Quien modifica la
 * entidad lo publica vía ApplicationEventPublisher; AuditLogEventListener lo
 * persiste. Los servicios de negocio no conocen la persistencia del audit.
 */
public record AuditableFieldChangedEvent(
        UUID companyId,
        String entityType,
        UUID entityId,
        UUID usuarioId,
        String campo,
        String valorAnterior,
        String valorNuevo) {
}
