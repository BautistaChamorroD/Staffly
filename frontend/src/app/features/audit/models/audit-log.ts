export interface AuditLogEntry {
  id: string;
  entityType: string;
  entityId: string;
  usuarioId: string;
  campo: string;
  valorAnterior: string | null;
  valorNuevo: string | null;
  fecha: string;
}

export interface AuditLogFilters {
  entidad?: string;
  entidadId?: string;
  userId?: string;
  desde?: string;
  hasta?: string;
}
